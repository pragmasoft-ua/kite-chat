## Inspiration

Pragmasoft, a Ukrainian software development team, will reach its 20th anniversary next year. The last time we seriously updated our website was for our 10th anniversary. The website was mostly static; the only functionality requiring a backend was the feedback form. I have been dissatisfied with that feedback form for all those years. It was frequently abused by spambots, and email notifications were sometimes lost in spam. Additionally, users lacked a clear indication that their feedback had been delivered and read. After careful consideration, I decided to replace it with a web chat. However, finding the perfect solution turned out to be challenging.

Some applications were more suitable for dedicated support offices, while others focused on automated responses. Most required the installation of dedicated mobile apps or offered limited, relatively complex, or premium integration with existing instant messengers.

What I wanted was a simple solution — adding a widget to my site and being able to respond to occasional requests directly in the existing instant messenger app on my phone. Telegram was chosen because it's the most popular messenger in Ukraine and because of its robust bot API. Also, Telegram allowed me to avoid writing a backend UI completely - it has much of the required functionality out of the box - including private groups, chat history, search, rich message types, and synchronization between desktop and mobile clients.

I considered developing only the Telegram integration myself. To my surprise, however, there was a lack of high-quality open-source web chat widgets. The ones I did find were tightly integrated with their services or web frameworks, so I decided to also create a general-purpose open-source chat widget, distributed as a web component module.

I started prototyping a few years ago, but due to the high demand for our services, I was unable to dedicate enough time to complete it. Now, with the market cooled down, amplified by the war in Ukraine, the workload from our long-term clients has reduced enough to allow me to resume work on this project. Additionally, two talented juniors recently joined our team, who could substantially improve their skills and at the same time help move this project forward.

## What it Does

K1te chat is a customizable live web chat widget that can be easily embedded into any static or dynamic website. It uses private Telegram groups as support channels to provide instant feedback to your website visitors.

The result is instantaneous bidirectional communication with the users of your website. All members of the support channel group can view and respond to incoming messages, as well as exchange files and images.

Furthermore, website users have the option to seamlessly transition to Telegram and continue the dialogue there, allowing them to avoid keeping a web chat page open continuously.

The backend is multi-tenant and allows multiple users to create as many support channels as they wish.

The frontend is implemented as a web component, so it can work with any frontend and backend framework and programming language, or without a framework at all.

## How We Built It

The serverless model suits the K1te chat functionality well. Chat communication is asynchronous and can tolerate serverless cold start delays while preserving costs down to zero for low-activity channels.

On the other hand, developing and debugging serverless functions locally is inconvenient.

Because of this, from the outset, I decided to implement a shared domain core that abstracts the deployment strategy, and two backends - containerized and serverless.

The domain core is framework agnostic, written in Java 17.

The containerized backend exposes core services using Quarkus RestEasy Reactive and is compiled into a Docker image deployed on any container service.

The serverless backend uses Quarkus Lambda support and is compiled into a native executable using GraalVM.

The serverless infrastructure is described using CDK for Terraform.

GitHub Actions are used for the CI/CD pipeline, but it also utilizes OpenShift s2i for the containerized backend and AWS CodeBuild for the serverless backend.

The frontend is implemented using the Lit web components framework.

The backend GitHub project's [README](https://github.com/pragmasoft-ua/kite-chat#readme) contains a detailed description of both containerized and serverless architectures, accompanied by diagrams.

## Challenges We Ran Into

Honestly, any technical challenges seem insignificant compared to the problems brought on by the war - such as regular air raids and damages to civil infrastructure.

Some challenges were related to the limitations of the Telegram Bot API and the stateless serverless model, as well as restrictions of some AWS services.

OpenShift deployment went surprisingly smoothly.

One issue I faced with OpenShift related to DNS configuration. On startup, the K1te server must register a webhook on the Telegram Bot API to receive updates. Only secure (https) webhooks are supported, which means the application should know its domain name before it's deployed and an OpenShift Route is created.

I was unable to find an easy way to obtain the canonical domain name from the OpenShift router before the Route was created, leading to a chicken-and-egg problem.

Another problem was organizational: I was unable to download the `oc` CLI utility due to export restrictions. I filed a support request, but the problem is still unresolved. Fortunately, I was able to use `oc` from another team member. Export restrictions seem odd because `oc` is open-source and anyone can build it from its source code.

## Accomplishments That We're Proud Of

We have a working solution deployed on both an OpenShift cluster and as a serverless stack on AWS.

It includes a small set of features we planned to implement, but those that are already in place are most useful for us.

Our open-source solution is modular and will hopefully be useful in different contexts to many others.

## What We Learned

I was surprised by how well Quarkus integrates with OpenShift. Just one command (actually two - Private Volume Claim had to be deployed separately) to build and deploy to the cluster.

When building both containerized and serverless backends, we gained a lot of practical experience in designing and optimizing such systems. We will use our code and knowledge as a reference solution for our customers' challenges.

We were able to compare constraints and advantages of both serverless and containerized platforms, measure valuable performance metrics, and optimize costs. I'm confident both deployment models are at the forefront of modern software development, and it was very educational for our juniors to participate in this task.

Regarding the frontend, it's clear that web components are becoming mainstream in the component model and will likely supersede other frontend frameworks eventually.

## What's Next for K1te Chat

We plan to extend K1te chat in many directions. On the horizon is an LLM-based automated assistant and localization to the Ukrainian language. We also plan to support integration with more messengers - WhatsApp and Discord are under consideration.
