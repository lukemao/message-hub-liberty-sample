IBM® Bluemix® Message Hub is a scalable, distributed, high throughput messaging service built on the top of Apache Kafka. It underpins the integration of your on-premise and off-premise cloud services and technologies. You can wire micro-services together using open protocols, connect stream data to analytics to realise powerful insight and feed event data to multiple applications to react in real time.

Liberty for Java™ applications on IBM® Bluemix® are powered by the IBM WebSphere® Liberty Buildpack. The Liberty profile is a highly composable, fast-to-start, dynamic application server runtime environment. It is part of IBM WebSphere Application Server v8.5.5.

This repository holds a sample cloud web application that were built using Liberty Java. The app will interact with a binded message hub service to produce and consume messages. 

For more information regarding IBM Message Hub, [view the documentation on Bluemix](https://www.ng.bluemix.net/docs/services/MessageHub/index.html).

Important Note: The samples in this repository will create topic(s) on your behalf - creating a topic incurs a fee. For more information, view the README files in each part of the repository and consult the Bluemix documentation if necessary.

##General Prerequisites

To build and run the sample, you must have the following installed:

* [git](https://git-scm.com/)
* [Gradle](https://gradle.org/)
* Java 7+
* [Message Hub Service Instance](https://console.ng.bluemix.net/catalog/services/message-hub/) provisioned in [IBM Bluemix](https://console.ng.bluemix.net/)

We assume the reader has relevant knowledge of above technologies. In addition, if you are not familiar with Liberty for Java on Bluemix, please consult the [documentation](https://console.ng.bluemix.net/docs/starters/liberty/index.html#liberty).

#General steps

To deploy and run the sample app: 
* Create a message hub service
* Pull the sample from github
* Update messagehub login jar location.
* Do a maven clean and install
* Use `cf push` to deploy the app to Bluemix e.g. `cf push MessageHubLibertyApp -p target/defaultServer`
* Open a browser and access the app.
* Press the button to produce message to Kafka, you can then see the consumed messages.

##Pull the sample project

You can clone and pull the project from the public git repository to your local work space.

##Setup and install the project

Once you have the project pulled to your local workspace, you need to update the service.xml file. 

You need to set the messagehub login jar location attribute. For example, by default on Bluemix,
```shell
${server.config.dir}/apps/MessageHubLibertyApp.war/WEB-INF/lib
 ```

For the `#USERNAME` and `#PASSWORD` fields can be automatically updated by the sample app, if you keep the text as it is. The app will retrieve the username and passwords from message hub VCAP service.  

Install the project using maven:
```shell
mvn clean install
 ```

You should see a directory called `target` created in your project home directory. A WAR file is created under `target/defaultServer`, as well as a copy of the server.xml file.

##Deploy the sample app to Bluemix

Now we can push the app to Bluemix:
```shell
cd ${LIBERTY_PROJECT_DIR}
cf push MessageHubLibertyApp -p target/defaultServer -m 256M
 ```

From the `cf push` log you can find out the hostname of the deployed app. For example:
```shell
requested state: started
instances: 1/1
usage: 256M x 1 instances
urls: messagehublibertyapp.mybluemix.net
last uploaded: Mon Feb 29 15:44:29 UTC 2016
stack: cflinuxfs2
buildpack: Liberty for Java(TM) (SVR-DIR, liberty-2016.2.0_0, buildpack-v2.5-20160209-1336, ibmjdk-1.8.0_20160108, env)
 ```

##Bind message hub to the liberty sample

Again, we use `cf` to bind the message hub service to the liberty app:
```shell
cf bind-service $LIBERTY_APP_NAME $BLUEMIX_SERVICE_NAME
 ```
Then, we restage the liberty app:
```shell
cf restage $LIBERTY_APP_NAME
 ```
Now your app should be live. You can access it using the given hostname, e.g.:
```shell
messagehublibertyapp.mybluemix.net
 ```

##Produce and consume messages

On the liberty app web page, you can produce a message by click on the button `post message`.

If the message was successfully produced and then consumed, then you can see the prompted message:

####Already consumed messages:
```shell
Message: [{"value":"This is a test message, msgId=0"}]. Offset: 1
 ```
