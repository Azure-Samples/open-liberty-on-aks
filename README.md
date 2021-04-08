---
page_type: sample
languages:
- java
products:
- azure
description: "Sample projects for developing and deploying Java applications with Open/WebSphere Liberty on an Azure Kubernetes Service cluster."
urlFragment: "open-liberty-on-aks"
---

# Open Liberty/WebSphere Liberty on Azure Kubernetes Service Samples

## Overview

[Azure Kubernetes Service (AKS)](https://azure.microsoft.com/services/kubernetes-service/) deploys and manages containerized applications more easily with a fully managed Kubernetes service. Azure Kubernetes Service (AKS) offers serverless Kubernetes, an integrated continuous integration and continuous delivery (CI/CD) experience, and enterprise-grade security and governance. Unite your development and operations teams on a single platform to rapidly build, deliver, and scale applications with confidence.

[Open Liberty](https://openliberty.io) is an IBM Open Source project that implements the Eclipse MicroProfile specifications and is also Java/Jakarta EE compatible. Open Liberty is fast to start up with a low memory footprint and supports live reloading for quick iterative development. It is simple to add and remove features from the latest versions of MicroProfile and Java/Jakarta EE. Zero migration lets you focus on what's important, not the APIs changing under you.

[WebSphere Liberty](https://www.ibm.com/cloud/websphere-liberty) architecture shares the same code base as the open sourced Open Liberty server runtime, which provides additional benefits such as low-cost experimentation, customization and seamless migration from open source to production.

This repository contains samples projects for deploying Java applications with Open Liberty/WebSphere Liberty on an Azure Kubernetes Service cluster.
These sample projects show how to use various features in Open Liberty/WebSphere Liberty and how to integrate with different Azure services.
Below table shows the list of samples available in this repository.

| Sample                           | Description                                | Guide                            |
|----------------------------------|--------------------------------------------|----------------------------------|
| [`javaee-app-simple-cluster`](javaee-app-simple-cluster) | Deploy a simple cluster of Java EE application with Open Liberty/WebSphere Liberty on an AKS cluster. | [howto-guide](https://docs.microsoft.com/azure/aks/howto-deploy-java-liberty-app) |

## Demo Notes

Fill this in:

| Azure resource | Link |
|----------------|------|
| Subscription | subName |
| Resource group | rgName |
| Azure Container Registry | acrName |
| AKS Cluster | MyAKSCluster |
| Deployed app URL | appUrl |

### Show the source code

* `tree javaee-app-simple-cluster`

* `cat javaee-app-simple-cluster/pom.xml`

   * Show use of `liberty-maven-plugin`.

* `cat javaee-app-simple-cluster/Dockerfile`

   * Mention this is where CI/CD comes in.

* `cat javaee-app-simple-cluster/src/main/liberty/config/server.xml`

### Connect to the cluster

```bash
alias k=kubectl
az aks get-credentials --resource-group rgname --name myAKSCluster --overwrite-existing
k get nodes
```

### View application deployment

```bash
k get openlibertyapplication javaee-app-simple-cluster
k get deployment javaee-app-simple-cluster
k get service javaee-app-simple-cluster
k get pods
```

### Visit the application

* Visit the cluster in the portal

* Select **Workloads**.

* Show the `java-ee-app-simple-cluster`.

* [Java EE Cafe](appUrl)

* You should see the pod name of your application replicas displayed at the top-left of the page. Wait for a few minutes and refresh the page to see a different pod name displayed due to load balancing provided by the AKS cluster.




