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

## Create a resource group

An Azure resource group is a logical group in which Azure resources are deployed and managed.  

Create a resource group called *java-liberty-project* using the [az group create](/cli/azure/group#az_group_create) command  in the *eastus* location. This resource group will be used later for creating the ACR instance, the AAG, and the AKS cluster.

```azurecli-interactive
RESOURCE_GROUP_NAME=java-liberty-project
az group create --name $RESOURCE_GROUP_NAME --location eastus
```

## Create an ACR instance

Use the [az acr create](/cli/azure/acr#az_acr_create) command to create the ACR instance. The following example creates an ACR instance named *youruniqueacrname*. Make sure *youruniqueacrname* is unique within Azure.

```azurecli-interactive
REGISTRY_NAME=youruniqueacrname
az acr create --resource-group $RESOURCE_GROUP_NAME --name $REGISTRY_NAME --sku Basic --admin-enabled
```

After a short time, you should see a JSON output that contains:

```output
  "provisioningState": "Succeeded",
  "publicNetworkAccess": "Enabled",
  "resourceGroup": "java-liberty-project",
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

## Create an AAG

You will need to create an AAG which will be used as the load balancer for your application running on the AKS later. Run the following commands to deploy an AAG:

```azurecli-interactive
wget https://raw.githubusercontent.com/oracle/weblogic-azure/main/weblogic-azure-aks/src/main/bicep/modules/_azure-resoruces/_appgateway.bicep -O appgateway.bicep

# If The following command exited with error "Failed to parse 'appgateway.bicep', please check whether it is a valid JSON format", pls run `az upgrade` to upgrade Azure CLI
result=$(az deployment group create -n testDeployment -g $RESOURCE_GROUP_NAME --template-file appgateway.bicep --parameters location=eastus)
APPGW_NAME=$(echo $result | jq -r '.properties.outputs.appGatewayName.value')
APPGW_VNET_NAME=$(echo $result | jq -r '.properties.outputs.vnetName.value')
APPGW_URL=$(echo $result | jq -r '.properties.outputs.appGatewayURL.value')
```

## Create an AKS cluster

Use the [az aks create](/cli/azure/aks#az_aks_create) command to create an AKS cluster. The following example creates a cluster named *myAKSCluster* with one node. This will take several minutes to complete.

```azurecli-interactive
CLUSTER_NAME=myAKSCluster
az aks create --resource-group $RESOURCE_GROUP_NAME --name $CLUSTER_NAME --node-count 1 --generate-ssh-keys --enable-managed-identity
```

After a few minutes, the command completes and returns JSON-formatted information about the cluster, including the following:

```output
  "nodeResourceGroup": "MC_java-liberty-project_myAKSCluster_eastus",
  "privateFqdn": null,
  "provisioningState": "Succeeded",
  "resourceGroup": "java-liberty-project",
```

### Add a user node pool to the AKS cluster

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

### Create network peers between the AAG and AKS cluster

To successfully communicate between the AAG and AKS cluster, we will need to create network peers between them. Run the following commands to peer two virtual networks:

```azurecli-interactive
aksMCRGName=$(az aks show -n $CLUSTER_NAME -g $RESOURCE_GROUP_NAME -o tsv --query "nodeResourceGroup")
aksNetWorkId=$(az resource list -g ${aksMCRGName} --resource-type Microsoft.Network/virtualNetworks -o tsv --query '[*].id')
aksNetworkName=$(az resource list -g ${aksMCRGName} --resource-type Microsoft.Network/virtualNetworks -o tsv --query '[*].name')
az network vnet peering create --name aks-appgw-peer --remote-vnet ${aksNetWorkId} --resource-group ${RESOURCE_GROUP_NAME} --vnet-name ${APPGW_VNET_NAME} --allow-vnet-access

appgwNetworkId=$(az resource list -g ${RESOURCE_GROUP_NAME} --name ${APPGW_VNET_NAME} -o tsv --query '[*].id')
az network vnet peering create --name aks-appgw-peer --remote-vnet ${appgwNetworkId} --resource-group ${aksMCRGName} --vnet-name ${aksNetworkName} --allow-vnet-access

# Associate the route table to Application Gateway's subnet
routeTableId=$(az network route-table list -g $aksMCRGName --query "[].id | [0]" -o tsv)
appGatewaySubnetId=$(az network application-gateway show -n $APPGW_NAME -g $RESOURCE_GROUP_NAME -o tsv --query "gatewayIpConfigurations[0].subnet.id")
az network vnet subnet update --ids $appGatewaySubnetId --route-table $routeTableId
```

### Connect to the AKS cluster

To manage a Kubernetes cluster, you use [kubectl](https://kubernetes.io/docs/reference/kubectl/overview/), the Kubernetes command-line client. If you use Azure Cloud Shell, `kubectl` is already installed. To install `kubectl` locally, use the [az aks install-cli](/cli/azure/aks#az_aks_install_cli) command:

```azurecli-interactive
az aks install-cli
```

To configure `kubectl` to connect to your Kubernetes cluster, use the [az aks get-credentials](/cli/azure/aks#az_aks_get_credentials) command. This command downloads credentials and configures the Kubernetes CLI to use them.

```azurecli-interactive
az aks get-credentials --resource-group $RESOURCE_GROUP_NAME --name $CLUSTER_NAME --overwrite-existing
```

To verify the connection to your cluster, use the [kubectl get]( https://kubernetes.io/docs/reference/generated/kubectl/kubectl-commands#get) command to return a list of the cluster nodes.

```azurecli-interactive
kubectl get nodes
```

The following example output shows the single node created in the previous steps. Make sure that the status of the node is *Ready*:

```output
NAME                                STATUS   ROLES   AGE     VERSION
aks-labelnp-xxxxxxxx-yyyyyyyyyy     Ready    agent   76s     v1.20.9
aks-nodepool1-xxxxxxxx-yyyyyyyyyy   Ready    agent   76s     v1.20.9
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

# Install the operator on the user node pool
wget https://raw.githubusercontent.com/OpenLiberty/open-liberty-operator/master/deploy/releases/0.7.1/openliberty-app-operator.yaml -O openliberty-app-operator.yaml
cat <<EOF >>openliberty-app-operator.yaml
      affinity:
        nodeAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            nodeSelectorTerms:
            - matchExpressions:
              - key: ${NODE_LABEL_KEY}
                operator: In
                values:
                - ${NODE_LABEL_VALUE}
EOF

cat openliberty-app-operator.yaml \
    | sed -e "s/OPEN_LIBERTY_WATCH_NAMESPACE/${WATCH_NAMESPACE}/" \
    | kubectl apply -n ${OPERATOR_NAMESPACE} -f -
```

## Install AAG Ingress Controller

```azurecli-interactive
# If you haven't installed Helm, pls install it first
# Install Helm from `apt`, more details pls see https://helm.sh/docs/intro/install/
curl https://baltocdn.com/helm/signing.asc | sudo apt-key add -
sudo apt-get install apt-transport-https --yes
echo "deb https://baltocdn.com/helm/stable/debian/ all main" | sudo tee /etc/apt/sources.list.d/helm-stable-debian.list
sudo apt-get update
sudo apt-get install helm

# Add the application-gateway-kubernetes-ingress helm repo and perform a helm update
helm repo add application-gateway-kubernetes-ingress https://appgwingress.blob.core.windows.net/ingress-azure-helm-package/
helm repo update

kubectl apply -f https://raw.githubusercontent.com/oracle/weblogic-azure/main/weblogic-azure-aks/src/main/arm/scripts/appgw-ingress-clusterAdmin-roleBinding.yaml

subID=<your-azure-subscription-id>
spBase64String=$(az ad sp create-for-rbac --sdk-auth | base64 -w0)
azureAppgwIngressVersion="1.4.0"

wget https://raw.githubusercontent.com/oracle/weblogic-azure/main/weblogic-azure-aks/src/main/arm/scripts/appgw-helm-config.yaml.template -O appgw-helm-config.yaml
sed -i -e "s:@SUB_ID@:${subID}:g" appgw-helm-config.yaml
sed -i -e "s:@APPGW_RG_NAME@:${RESOURCE_GROUP_NAME}:g" appgw-helm-config.yaml
sed -i -e "s:@APPGW_NAME@:${APPGW_NAME}:g" appgw-helm-config.yaml
sed -i -e "s:@WATCH_NAMESPACE@:${WATCH_NAMESPACE}:g" appgw-helm-config.yaml
sed -i -e "s:@SP_ENCODING_CREDENTIALS@:${spBase64String}:g" appgw-helm-config.yaml

helm install ingress-azure \
    -f appgw-helm-config.yaml \
    application-gateway-kubernetes-ingress/ingress-azure \
    --version ${azureAppgwIngressVersion}
```

## Build application image

To deploy and run your Liberty application on the AKS cluster, containerize your application as a Docker image using [Open Liberty container images](https://github.com/OpenLiberty/ci.docker) or [WebSphere Liberty container images](https://github.com/WASdev/ci.docker).

1. Clone the sample code for this guide. The sample is on [GitHub](https://github.com/Azure-Samples/open-liberty-on-aks).
1. Locate to your local clone and run `git checkout lg-sample` to checkout branch `lg-sample`.
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

To avoid Azure charges, you should clean up unnecessary resources.  When the cluster is no longer needed, use the [az group delete](/cli/azure/group#az_group_delete) command to remove the resource group, container service, container registry, and all related resources.

```azurecli-interactive
az group delete --name $RESOURCE_GROUP_NAME --yes --no-wait
```

Delete the service principal used for AAG Ingress Controller:

```azurecli-interactive
az ad sp delete --id $(echo $spBase64String | base64 -d | jq -r '.clientId')
```
