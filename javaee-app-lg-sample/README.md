# Deploy a Java application with Open Liberty or WebSphere Liberty on an Azure Kubernetes Service (AKS) cluster

This article demonstrates how to:

* Create an AKS cluster, an Azure Container Registry (ACR) instance, and an Azure Application Gateway (AAG).
* Integrate the ACR and the AAG with the AKS.
* Run your Java, Java EE, Jakarta EE, or MicroProfile application on the Open Liberty or WebSphere Liberty runtime.
* Build the application Docker image using Open Liberty container images.
* Deploy the containerized application to an AKS cluster using the Open Liberty Operator.

The Open Liberty Operator simplifies the deployment and management of applications running on Kubernetes clusters. With Open Liberty Operator, you can also perform more advanced operations, such as gathering traces and dumps.

For more details on Open Liberty, see [the Open Liberty project page](https://openliberty.io/). For more details on IBM WebSphere Liberty, see [the WebSphere Liberty product page](https://www.ibm.com/cloud/websphere-liberty).

[!INCLUDE [quickstarts-free-trial-note](../../includes/quickstarts-free-trial-note.md)]

[!INCLUDE [azure-cli-prepare-your-environment.md](../../includes/azure-cli-prepare-your-environment.md)]

* This article requires the latest version of Azure CLI. If using Azure Cloud Shell, the latest version is already installed.
* If running the commands in this guide locally (instead of Azure Cloud Shell):
  * Prepare a local machine with Unix-like operating system installed (for example, Ubuntu, macOS, Windows Subsystem for Linux).
  * Install a Java SE implementation (for example, [AdoptOpenJDK OpenJDK 8 LTS/OpenJ9](https://adoptopenjdk.net/?variant=openjdk8&jvmVariant=openj9)).
  * Install [Maven](https://maven.apache.org/download.cgi) 3.5.0 or higher.
  * Install [Docker](https://docs.docker.com/get-docker/) for your OS.
  * Install [`jq`](https://stedolan.github.io/jq/download/).

## Set up the infrastructure

In this section, you will create user-assigned manage identites, a virtual network, an AKS cluster, an AAG instance, and install Azure Application Gateway Controller (AGIC) using Helm.

```azurecli-interactive
# Create resource groups
UAMI_RG_NAME=<uami-rg-name>
VNET_RG_NAME=<vnet-rg-name>
AG_RG_NAME=<ag-rg-name>
AKS_RG_NAME=<aks-rg-name>
az group create --name ${UAMI_RG_NAME} --location eastus
az group create --name ${VNET_RG_NAME} --location eastus
az group create --name ${AG_RG_NAME} --location eastus
az group create --name ${AKS_RG_NAME} --location eastus

# Create identities for AKS cluster
CLUSTER_IDENTITY_NAME=cluster-identity
KUBELET_IDENTITY_NAME=kubelet-identity
az identity create --name ${CLUSTER_IDENTITY_NAME} --resource-group ${UAMI_RG_NAME}
az identity create --name ${KUBELET_IDENTITY_NAME} --resource-group ${UAMI_RG_NAME}
clusterIdentityId=$(az identity show -n ${CLUSTER_IDENTITY_NAME} -g ${UAMI_RG_NAME} -o tsv --query "id")
kubeletIdentityId=$(az identity show -n ${KUBELET_IDENTITY_NAME} -g ${UAMI_RG_NAME} -o tsv --query "id")

# Create vNet and subnets
VNET_NAME=myVnet
AG_SUBNET_NAME=agSubnet
AKS_SUBNET_NAME=aksSubnet
az network vnet create -n ${VNET_NAME} -g ${VNET_RG_NAME} --address-prefix 10.0.0.0/8
az network vnet subnet create -n ${AG_SUBNET_NAME} --address-prefixes 10.241.0.0/16 --vnet-name ${VNET_NAME} -g ${VNET_RG_NAME}
az network vnet subnet create -n ${AKS_SUBNET_NAME} --address-prefixes 10.240.0.0/16 --vnet-name ${VNET_NAME} -g ${VNET_RG_NAME}
agSubnetId=$(az network vnet subnet show -n ${AG_SUBNET_NAME} --vnet-name ${VNET_NAME} -g ${VNET_RG_NAME} --query "id" --output tsv)
aksSubnetId=$(az network vnet subnet show -n ${AKS_SUBNET_NAME} --vnet-name ${VNET_NAME} -g ${VNET_RG_NAME} --query "id" --output tsv)

# Create Application Gateway
PUBLIC_IP_NAME=myPublicIp
az network public-ip create -n ${PUBLIC_IP_NAME} -g ${AG_RG_NAME} --allocation-method Static --sku Standard
APPGW_NAME=myApplicationGateway
az network application-gateway create \
    --name ${APPGW_NAME} \
    --resource-group ${AG_RG_NAME} \
    --location eastus \
    --sku Standard_v2 \
    --public-ip-address ${PUBLIC_IP_NAME} \
    --subnet ${agSubnetId}
appgwId=$(az network application-gateway show -n ${APPGW_NAME} -g ${AG_RG_NAME} -o tsv --query "id")

# Create AKS cluster
AKS_CLUSTER_NAME=akscluster
az aks create \
    --resource-group ${AKS_RG_NAME} \
    --name ${AKS_CLUSTER_NAME} \
    --node-count 1 \
    --enable-managed-identity \
    --assign-identity ${clusterIdentityId} \
    --assign-kubelet-identity ${kubeletIdentityId} \
    --network-plugin azure \
    --vnet-subnet-id ${aksSubnetId} \
    --docker-bridge-address 172.17.0.1/16 \
    --dns-service-ip 10.2.0.10 \
    --service-cidr 10.2.0.0/24 \
    --generate-ssh-keys
az aks get-credentials -n ${AKS_CLUSTER_NAME} -g ${AKS_RG_NAME} --overwrite-existing

# Role assignments required by AAD Pod Identity
export SUBSCRIPTION_ID=$(az account show --query id -o tsv)
export RESOURCE_GROUP=${AKS_RG_NAME}
export CLUSTER_NAME=${AKS_CLUSTER_NAME}
export IDENTITY_RESOURCE_GROUP=${UAMI_RG_NAME}
curl -s https://raw.githubusercontent.com/Azure/aad-pod-identity/master/hack/role-assignment.sh | bash

# Deploy AAD Pod Identity
kubectl apply -f https://raw.githubusercontent.com/Azure/aad-pod-identity/master/deploy/infra/deployment-rbac.yaml
# For AKS clusters, deploy the MIC and AKS add-on exception by running -
kubectl apply -f https://raw.githubusercontent.com/Azure/aad-pod-identity/master/deploy/infra/mic-exception.yaml

# Assign roles to the uami used in the AGIC
principalId=$(az identity show -n ${CLUSTER_IDENTITY_NAME} -g ${UAMI_RG_NAME} --query principalId -o tsv)
az role assignment create --role Contributor --assignee ${principalId} --scope ${appgwId}
az role assignment create --role Reader --assignee ${principalId} --scope $(az group show -n ${AG_RG_NAME} --query id -o tsv)

# Grant azure ingress permission
cat <<EOF | kubectl apply -f -
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: ingress-azure-admin
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: cluster-admin
subjects:
- kind: ServiceAccount
  name: ingress-azure
  namespace: default
EOF

# Install AGIC
rm -f appgw-helm-config.yaml
cat >> appgw-helm-config.yaml <<EOF
# Based on https://raw.githubusercontent.com/Azure/application-gateway-kubernetes-ingress/master/docs/examples/sample-helm-config.yaml
verbosityLevel: 3
appgw:
    subscriptionId: @SUB_ID@
    resourceGroup: @APPGW_RG_NAME@
    name: @APPGW_NAME@
    usePrivateIP: false
    shared: false
kubernetes:
    watchNamespace: @WATCH_NAMESPACE@
armAuth:
    type: aadPodIdentity
    identityResourceID: @IDENTITY_RESOURCE_ID@
    identityClientID: @IDENTITY_CLIENT_ID@
rbac:
    create: true
EOF

AGIC_WATCH_NAMESPACE='""'
clusterIdentityClientId=$(az identity show -n ${CLUSTER_IDENTITY_NAME} -g ${UAMI_RG_NAME} -o tsv --query clientId)
sed -i -e "s:@SUB_ID@:${SUBSCRIPTION_ID}:g" appgw-helm-config.yaml
sed -i -e "s:@APPGW_RG_NAME@:${AG_RG_NAME}:g" appgw-helm-config.yaml
sed -i -e "s:@APPGW_NAME@:${APPGW_NAME}:g" appgw-helm-config.yaml
sed -i -e "s:@WATCH_NAMESPACE@:${AGIC_WATCH_NAMESPACE}:g" appgw-helm-config.yaml
sed -i -e "s:@IDENTITY_RESOURCE_ID@:${clusterIdentityId}:g" appgw-helm-config.yaml
sed -i -e "s:@IDENTITY_CLIENT_ID@:${clusterIdentityClientId}:g" appgw-helm-config.yaml

azureAppgwIngressVersion="1.4.0"
helm install ingress-azure \
    -f appgw-helm-config.yaml \
    application-gateway-kubernetes-ingress/ingress-azure \
    --version ${azureAppgwIngressVersion}
```

## Create an ACR instance

Use the [az acr create](/cli/azure/acr#az_acr_create) command to create the ACR instance. The following example creates an ACR instance named *youruniqueacrname*. Make sure *youruniqueacrname* is unique within Azure.

```azurecli-interactive
RESOURCE_GROUP_NAME=${AKS_RG_NAME}
REGISTRY_NAME=youruniqueacrname
az acr create --resource-group $RESOURCE_GROUP_NAME --name $REGISTRY_NAME --sku Basic --admin-enabled
```

After a short time, you should see a JSON output that contains:

```output
  "provisioningState": "Succeeded",
  "publicNetworkAccess": "Enabled",
  "resourceGroup": "<aks-rg-name>",
```

### Connect to the ACR instance

You will need to sign in to the ACR instance before you can push an image to it. Run the following commands to verify the connection:

```azurecli-interactive
LOGIN_SERVER=$(az acr show -n $REGISTRY_NAME --query 'loginServer' -o tsv)
USER_NAME=$(az acr credential show -n $REGISTRY_NAME --query 'username' -o tsv)
PASSWORD=$(az acr credential show -n $REGISTRY_NAME --query 'passwords[0].value' -o tsv)

docker login $LOGIN_SERVER -u $USER_NAME -p $PASSWORD
```

You should see `Login Succeeded` at the end of command output if you have logged into the ACR instance successfully.

## Add a user node pool to the AKS cluster

To run your application on a user node pool, you will need to add it beforehand. Run the following commands to add a user nood pool:

```azurecli-interactive
NODE_LABEL_KEY=sku
NODE_LABEL_VALUE=gpu
az aks nodepool add \
    --resource-group $RESOURCE_GROUP_NAME \
    --cluster-name $CLUSTER_NAME \
    --name labelnp \
    --node-count 1 \
    --labels ${NODE_LABEL_KEY}=${NODE_LABEL_VALUE}

# Optional: list node pools
az aks nodepool list -g $RESOURCE_GROUP_NAME --cluster-name $CLUSTER_NAME
```

## Install Open Liberty Operator

After creating and connecting to the cluster, install the [Open Liberty Operator](https://github.com/OpenLiberty/open-liberty-operator/tree/master/deploy/releases/0.7.1) by running the following commands.

```azurecli-interactive
OPERATOR_NAMESPACE=default
WATCH_NAMESPACE='""'

# Install Custom Resource Definitions (CRDs) for OpenLibertyApplication
kubectl apply -f https://raw.githubusercontent.com/OpenLiberty/open-liberty-operator/master/deploy/releases/0.7.1/openliberty-app-crd.yaml

# Install cluster-level role-based access
curl -L https://raw.githubusercontent.com/OpenLiberty/open-liberty-operator/master/deploy/releases/0.7.1/openliberty-app-cluster-rbac.yaml \
      | sed -e "s/OPEN_LIBERTY_OPERATOR_NAMESPACE/${OPERATOR_NAMESPACE}/" \
      | kubectl apply -f -

# Install the operator
curl -L https://raw.githubusercontent.com/OpenLiberty/open-liberty-operator/master/deploy/releases/0.7.1/openliberty-app-operator.yaml \
      | sed -e "s/OPEN_LIBERTY_WATCH_NAMESPACE/${WATCH_NAMESPACE}/" \
      | kubectl apply -n ${OPERATOR_NAMESPACE} -f -
```

## Build application image

To deploy and run your Liberty application on the AKS cluster, containerize your application as a Docker image using [Open Liberty container images](https://github.com/OpenLiberty/ci.docker) or [WebSphere Liberty container images](https://github.com/WASdev/ci.docker).

1. Clone the sample code for this guide. The sample is on [GitHub](https://github.com/Azure-Samples/open-liberty-on-aks).
1. Locate to your local clone and run `git checkout lg-sample-agic-helm-identity` to checkout branch `lg-sample-agic-helm-identity`.
1. Run `cd javaee-app-lg-sample` to change directory to `javaee-app-lg-sample` of your local clone.
1. Run `mvn clean package` to package the application.
1. Run `mvn liberty:dev` to test the application. You should see `The defaultServer server is ready to run a smarter planet.` in the command output if successful. Use `CTRL-C` to stop the application.
1. Retrieve values for properties `artifactId` and `version` defined in the `pom.xml`.

   ```azurecli-interactive
   artifactId=$(mvn -q -Dexec.executable=echo -Dexec.args='${project.artifactId}' --non-recursive exec:exec)
   version=$(mvn -q -Dexec.executable=echo -Dexec.args='${project.version}' --non-recursive exec:exec)
   ```

1. Run `cd target` to change directory to the build of the sample.
1. Run one of the following commands to build the application image and push it to the ACR instance.
   * Build with Open Liberty base image if you prefer to use Open Liberty as a lightweight open source Java™ runtime:

     ```azurecli-interactive
     # Build and tag application image. This will cause the ACR instance to pull the necessary Open Liberty base images.
     az acr build -t ${artifactId}:${version} -r $REGISTRY_NAME .
     ```

   * Build with WebSphere Liberty base image if you prefer to use a commercial version of Open Liberty:

     ```azurecli-interactive
     # Build and tag application image. This will cause the ACR instance to pull the necessary WebSphere Liberty base images.
     az acr build -t ${artifactId}:${version} -r $REGISTRY_NAME --file=Dockerfile-wlp .
     ```

## Deploy application on the AKS cluster

Follow steps below to deploy the Liberty application on the AKS cluster.

1. Create a namespace for the sample.

   ```azurecli-interactive
   APPLICATION_NAMESPACE=javaee-app-lg-sample-namespace
   kubectl create namespace ${APPLICATION_NAMESPACE}
   ```

1. Create a pull secret so that the AKS cluster is authenticated to pull image from the ACR instance.

   ```azurecli-interactive
   PULL_SECRET_NAME=javaee-app-lg-sample-pull-secret
   kubectl create secret docker-registry ${PULL_SECRET_NAME} \
      --docker-server=${LOGIN_SERVER} \
      --docker-username=${USER_NAME} \
      --docker-password=${PASSWORD} \
      --namespace=${APPLICATION_NAMESPACE}
   ```

1. Verify the current working directory is `javaee-app-lg-sample/target` of your local clone.
1. Run the following commands to deploy your Liberty application with 3 replicas to the AKS cluster. Command output is also shown inline.

   ```azurecli-interactive
   # Create OpenLibertyApplication "javaee-app-lg-sample"
   APPLICATION_NAME=javaee-app-lg-sample
   REPLICAS=3

   cat openlibertyapplication.yaml \
       | sed -e "s/\${APPLICATION_NAME}/${APPLICATION_NAME}/g" \
       | sed -e "s/\${APPLICATION_NAMESPACE}/${APPLICATION_NAMESPACE}/g" \
       | sed -e "s/\${REPLICAS}/${REPLICAS}/g" \
       | sed -e "s/\${LOGIN_SERVER}/${LOGIN_SERVER}/g" \
       | sed -e "s/\${PULL_SECRET_NAME}/${PULL_SECRET_NAME}/g" \
       | sed -e "s/\${NODE_LABEL_KEY}/${NODE_LABEL_KEY}/g" \
       | sed -e "s/\${NODE_LABEL_VALUE}/${NODE_LABEL_VALUE}/g" \
       | kubectl apply -f -

   openlibertyapplication.openliberty.io/javaee-app-lg-sample created

   # Check if OpenLibertyApplication instance is created
   kubectl get openlibertyapplication ${APPLICATION_NAME} -n ${APPLICATION_NAMESPACE}

   NAME                        IMAGE                                                   EXPOSED   RECONCILED   AGE
   javaee-app-lg-sample        youruniqueacrname.azurecr.io/javaee-cafe:1.0.0          True         59s

   # Check if deployment created by Operator is ready
   kubectl get deployment ${APPLICATION_NAME} -n ${APPLICATION_NAMESPACE} --watch

   NAME                        READY   UP-TO-DATE   AVAILABLE   AGE
   javaee-app-lg-sample        0/3     3            0           20s
   ```

1. Wait until you see `3/3` under the `READY` column and `3` under the `AVAILABLE` column, use `CTRL-C` to stop the `kubectl` watch process.

1. Run the following commands to deploy Ingress resource for routing client requests to your deployed application. Command output is also shown inline.

   ```azurecli-interactive
   # Create Ingress "javaee-app-lg-sample-ingress"
   APPLICATION_INGRESS=javaee-app-lg-sample-ingress

   cat appgw-cluster-ingress.yaml \
       | sed -e "s/\${APPLICATION_INGRESS}/${APPLICATION_INGRESS}/g" \
       | sed -e "s/\${APPLICATION_NAMESPACE}/${APPLICATION_NAMESPACE}/g" \
       | sed -e "s/\${APPLICATION_NAME}/${APPLICATION_NAME}/g" \
       | kubectl apply -f -

   ingress.networking.k8s.io/javaee-app-lg-sample-ingress created

   # Check if Ingress instance is created
   kubectl get ingress ${APPLICATION_INGRESS} -n ${APPLICATION_NAMESPACE}

   NAME                           CLASS    HOSTS   ADDRESS        PORTS   AGE
   javaee-app-lg-sample-ingress   <none>   *       20.62.178.13   80      17s
   ```

### Test the application

To get public IP address of the Ingress, use the [kubectl get ingress](https://kubernetes.io/docs/reference/generated/kubectl/kubectl-commands#get) command with the `--watch` argument.

```azurecli-interactive
kubectl get ingress ${APPLICATION_INGRESS} -n ${APPLICATION_NAMESPACE} --watch

NAME                           CLASS    HOSTS   ADDRESS        PORTS   AGE
javaee-app-lg-sample-ingress   <none>   *       20.62.178.13   80      5m49s
```

Once the *ADDRESS* represents to an actual public IP address, use `CTRL-C` to stop the `kubectl` watch process.

Open a web browser to the external IP address of your Ingress (`20.62.178.13` for the above example) to see the application home page. You should see the pod name of your application replicas displayed at the top-left of the page. The another way to visit the application is to copy the value of environment `APPGW_URL` defined at the previous section, and paste it to the browser.

## Clean up the resources

To avoid Azure charges, you should clean up unnecessary resources. When the cluster is no longer needed, use the [az group delete](/cli/azure/group#az_group_delete) command to remove the resource group, container service, container registry, and all related resources.

```azurecli-interactive
az group delete --name ${AKS_RG_NAME} --yes --no-wait
az group delete --name ${AG_RG_NAME} --yes --no-wait
az group delete --name ${VNET_RG_NAME} --yes --no-wait
az group delete --name ${UAMI_RG_NAME} --yes --no-wait
```
