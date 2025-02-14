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

[Open Liberty](https://openliberty.io) is an IBM Open Source project that implements the Eclipse MicroProfile specifications and is also Jakarta EE compatible. Open Liberty is fast to start up with a low memory footprint and supports live reloading for quick iterative development. It is simple to add and remove features from the latest versions of MicroProfile and Jakarta EE. Zero migration lets you focus on what's important, not the APIs changing under you.

[WebSphere Liberty](https://www.ibm.com/cloud/websphere-liberty) architecture shares the same code base as the open sourced Open Liberty server runtime, which provides additional benefits such as low-cost experimentation, customization and seamless migration from open source to production.

This repository contains sample projects for deploying Java applications with Open Liberty/WebSphere Liberty on an Azure Kubernetes Service cluster.
These sample projects show how to use various features in Open Liberty/WebSphere Liberty and how to integrate with different Azure services.
Below table shows the list of samples available in this repository.

| Sample                           | Description                                | Guide                            |
|----------------------------------|--------------------------------------------|----------------------------------|
| [`java-app`](java-app) | Deploy a simple Jakarta EE application with Open Liberty/WebSphere Liberty on an AKS cluster. | [howto-guide](https://docs.microsoft.com/azure/aks/howto-deploy-java-liberty-app) |
| [`java-app-jcache`](java-app-jcache) | Using Azure Redis as session cache for WebSphere Liberty or Open Liberty. | [howto-guide](https://learn.microsoft.com/azure/developer/java/ee/how-to-deploy-java-liberty-jcache). The guide is simpfiled to run a Liberty app locally which enables persistence of HTTP session cache using JCache and stores session data in the Azure Managed Redis instance. To learn how to deploy the app onto AKS cluster, see [the original version of the guide](https://github.com/azure-javaee/azure-dev-docs-pr/blob/20250214/articles/java/ee/how-to-deploy-java-liberty-jcache.md). |
