/**
 * Copyright 2016 IBM
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
/**
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corp. 2016
 */
package com.messagehub.samples.servlet;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messagehub.samples.env.MessageHubCredentials;
import com.messagehub.samples.env.MessageHubEnvironment;

/**
 * Servlet implementation class KafkaServlet
 */
@WebServlet("/KafkaServlet")
public class KafkaServlet extends HttpServlet {
    private final Logger logger = Logger.getLogger(KafkaServlet.class);
    private final String userDir=System.getProperty("user.dir");
    private final String resourceDir=userDir + File.separator + "apps" + File.separator + "MessageHubLibertyApp.war" + File.separator + "resources";
    //private static boolean isDistribution;
    private KafkaProducer<byte[], byte[]> kafkaProducer;
    private ConsumerRunnable consumerRunnable;
    private String kafkaHost, restHost, apiKey, topic="testTopic";
    private String producedMessage, currentConsumedMessage;
    private int producedMessages = 0;
    private Thread consumerThread = null;
    //private String vcapServices;
    
    private boolean canProduce = false;
    
    private boolean messageProduced;
    
    /**
     * Intialising the KafkaServlet
     */
    public void init() throws ServletException {
    	logger.log(Level.WARN, "Initialising Kafka Servlet");

    	messageProduced = false;
    	producedMessages = 0;
    	//==== Retrieving kafka-Host, rest-Host and api-key from message hub vcap service ===
        
        logger.log(Level.WARN, "Running in local mode.");
        
        // Set JAAS configuration property.
        if(System.getProperty("java.security.auth.login.config") == null) {
            System.setProperty("java.security.auth.login.config", "");
        }
        
        // Arguments parsed via VCAP_SERVICES environment variable.
        // Retrieve VCAP json through Bluemix system environment variable "VCAP_SERVICES"
        String vcapServices = System.getenv("VCAP_SERVICES");
        logger.log(Level.WARN, "VCAP_SERVICES: \n"+vcapServices);
        ObjectMapper mapper = new ObjectMapper();
        if(vcapServices != null) {
            try {
                // Parse VCAP_SERVICES into Jackson JsonNode, then map the 'messagehub' entry
                // to an instance of MessageHubEnvironment.
                JsonNode vcapServicesJson = mapper.readValue(vcapServices, JsonNode.class);
                ObjectMapper envMapper = new ObjectMapper();
                
                if(vcapServicesJson.has("messagehub")) {
                    MessageHubEnvironment messageHubEnvironment = envMapper.readValue(vcapServicesJson.get("messagehub").get(0).toString(), MessageHubEnvironment.class);
                    MessageHubCredentials credentials = messageHubEnvironment.getCredentials();
                    
                    kafkaHost = credentials.getKafkaBrokersSasl()[0];
                    restHost = credentials.getKafkaRestUrl();
                    apiKey = credentials.getApiKey();
                    
                } else {
                	logger.log(Level.ERROR, "Error while parsing VCAP_SERVICES: A Message Hub service instance is not bound to this application.");
                    return;
                }
            } catch(final Exception e) {
                e.printStackTrace();
                return;
            }
        } else {
        	logger.log(Level.ERROR, "VCAP_SERVICES environment variable is null.");
            return;
        }
        
        // Check topic
        RESTRequest restApi = new RESTRequest(restHost, apiKey);
        // Create a topic, ignore a 422 response - this means that the
        // topic name already exists.
        restApi.post("/admin/topics", "{ \"name\": \"" + topic + "\" }", new int[] { 422 });
        
        String topics = restApi.get("/admin/topics", false);
        logger.log(Level.WARN, "Topics: " + topics);
    	
    	// Initialise Kafka Producer
    	kafkaProducer = new KafkaProducer<byte[], byte[]>(getClientConfiguration(kafkaHost, apiKey, true));
        
    	// Initialise Kafka Consumer
    	consumerRunnable = new ConsumerRunnable(kafkaHost, apiKey, topic);
        consumerThread = new Thread(consumerRunnable);
        consumerThread.start();
        
        try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
        
    }
    
    /**
     * Getting the content of server.xml
     * @return XML in a string
     */
    private String getServerXML(){
    	String serverXmlContents = "";
    	try{
    		String serverXmlPath = System.getProperty("server.config.dir")+ File.separator+"server.xml";
    		serverXmlContents = new String(Files.readAllBytes(Paths.get(serverXmlPath)));
    	}catch(IOException ie){
    		logger.log(Level.ERROR, "Unable to retrieve server.xml");
    	}
    	return serverXmlContents;
    }
    
    /**
     * Convert xml to be able to be displayed in HTML
     * @param xml
     * @return
     */
    private String xmlInHtml(String xml) {
    	return xml.replaceAll("&", "&amp").replaceAll("<", "&lt").replaceAll(">","&gt").replaceAll("\"", "&quot").replaceAll("'","&apos");
    }
    
    /**
     * Returns the latest messages received from the Kafka Consumer.
     */
    @Override
    protected void doGet(HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
    	response.setContentType("text/html");
    	//response.getWriter().print("<p>========== VCAP Service =========== </p>");
    	//response.getWriter().print("<div><small class='code'>"+vcapServices +"</small></div>");
    	response.getWriter().print("<p>========== Message Hub Info =========== </p>");
        response.getWriter().print("<p>restHost= <small class='code'>"+restHost+"</small></p>");
        response.getWriter().print("<p>kafkaHost= <small class='code'>"+kafkaHost+"</small></p>");
        response.getWriter().print("<p>apiKey= <small class='code'>"+apiKey+"</small></p>");
    	response.getWriter().print("<p>========== Liberty Info =========== </p>");
        response.getWriter().print("<p>user.dir= <small class='code'>"+System.getProperty("user.dir")+"</small></p>");
        response.getWriter().print("<p>server.config.dir= <small class='code'>"+System.getProperty("server.config.dir")+"</small></p>");
        // Test if the cacert exists
    	String javaCertPath = userDir + "/../../../../.java/jre/lib/security/cacerts";
        response.getWriter().print("<p>java cert exist? = <small class='code'>"+Paths.get(javaCertPath).toFile().exists()+"</small></p>");
        response.getWriter().print("<p>Checking server.xml contains correct message hub required login information: </p>");
        String serverXml = getServerXML();
        response.getWriter().print("<div><small class='code'>"+xmlInHtml(serverXml)+"</small></div>");
    	response.getWriter().print("<p>========== Kafka Info =========== </p>");
        response.getWriter().print("<p>Already consumed messages: </p>");
        for(String s : consumerRunnable.consumedMessages){
        	response.getWriter().print("<p><small class='code'>Message: "+s+"</small></p>");
        }
        
    }
    
    /**
     * Produces a message to a Message Hub endpoint.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    	response.setContentType("text/html");
        
        //Producing messages to a topic
        produce(topic);
        
        try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
        
        if(messageProduced){
        	response.getWriter().print("<p>We have produced a message: <small class='code'>"+producedMessage+"</small> </p>");
	        //Print out consumed messages
	        response.getWriter().print("<p>Consumed messages: <small class='code'>"+currentConsumedMessage+"</small></p>");
        }
    }
   
    /**
     * Retrieve client configuration information, using a properties file, for
     * connecting to secure Kafka.
     * 
     * @param broker
     *            {String} A string representing a list of brokers the producer
     *            can contact.
     * @param apiKey
     *            {String} The API key of the Bluemix Message Hub service.
     * @param isProducer
     *            {Boolean} Flag used to determine whether or not the
     *            configuration is for a producer.
     * @return {Properties} A properties object which stores the client
     *         configuration info.
     */
    public final Properties getClientConfiguration(String broker,
            String apiKey, boolean isProducer) {
        Properties props = new Properties();
        InputStream propsStream;
        String fileName;

        if (isProducer) {
            fileName = "producer.properties";
        } else {
            fileName = "consumer.properties";
        }

        try {
            propsStream = new FileInputStream(resourceDir + File.separator + fileName);
            props.load(propsStream);
            propsStream.close();
        } catch (IOException e) {
            return props;
        }

        props.put("bootstrap.servers", broker);
        
        props.put("ssl.truststore.location", userDir + "/../../../../.java/jre/lib/security/cacerts");

        return props;
    }

    /**
     * Produce a message to a <code>topic</code>
     * @param topic
     */
    public void produce(String topic){
    	logger.log(Level.WARN, "Producer is starting.");
    	
    	String fieldName = "records";
        // Push a message into the list to be sent.
        MessageList list = new MessageList();
        producedMessage = "This is a test message, msgId=" + producedMessages;
        list.push(producedMessage);

        try {
            // Create a producer record which will be sent
            // to the Message Hub service, providing the topic
            // name, field name and message. The field name and
            // message are converted to UTF-8.
            ProducerRecord<byte[], byte[]> record = new ProducerRecord<byte[], byte[]>(
                    topic, fieldName.getBytes("UTF-8"), list.build()
                            .getBytes("UTF-8"));

            // Synchronously wait for a response from Message Hub / Kafka.
            RecordMetadata m = kafkaProducer.send(record).get();
            producedMessages++;
            
            logger.log(Level.WARN, "Message produced, offset: " + m.offset());

            Thread.sleep(1000);
        } catch (final Exception e) {
            e.printStackTrace();
            // Consumer will hang forever, so exit program.
            System.exit(-1);
        }
        messageProduced = true;
        logger.log(Level.WARN, "Producer is shutting down.");
    }
    
    public String toPrettyString(String xml, int indent) {
        try {
            // Turn xml string into a document
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new InputSource(new ByteArrayInputStream(xml.getBytes("utf-8"))));

            // Remove whitespaces outside tags
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodeList = (NodeList) xPath.evaluate("//text()[normalize-space()='']",
                                                          document,
                                                          XPathConstants.NODESET);

            for (int i = 0; i < nodeList.getLength(); ++i) {
                Node node = nodeList.item(i);
                node.getParentNode().removeChild(node);
            }

            // Setup pretty print options
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setAttribute("indent-number", indent);
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");

            // Return pretty print xml string
            StringWriter stringWriter = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(stringWriter));
            return stringWriter.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Kafka consumer runnable which can be used to create and run consumer as a separate thread.
     * @author Admin
     *
     */
    class ConsumerRunnable implements Runnable {
        private KafkaConsumer<byte[], byte[]> kafkaConsumer;
        private ArrayList<String> topicList;
        private boolean closing;
        private ArrayList<String> consumedMessages;

        ConsumerRunnable(String broker, String apiKey, String topic) {
        	consumedMessages=new ArrayList<String>();
            closing = false;
            topicList = new ArrayList<String>();

            // Provide configuration and deserialisers
            // for the key and value fields received.
            kafkaConsumer = new KafkaConsumer<byte[], byte[]>(getClientConfiguration(broker, apiKey, false),
                    new ByteArrayDeserializer(), new ByteArrayDeserializer());

            topicList.add(topic);
            kafkaConsumer.subscribe(topicList, new ConsumerRebalanceListener() {

                @Override
                public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                }

                @Override
                public void onPartitionsAssigned(Collection<TopicPartition> partitions)  {
                	try {
	                	logger.log(Level.WARN, "Partitions assigned, consumer seeking to end.");
	
	                    for (TopicPartition partition : partitions) {
	                    	long position = kafkaConsumer.position(partition);
	                    	logger.log(Level.WARN, "current Position: " + position);
	
	                    	logger.log(Level.WARN, "Seeking to end...");
	                        kafkaConsumer.seekToEnd(partition);
	                    	//kafkaConsumer.seek(partition, 0);
	                    	logger.log(Level.WARN, "Seek from the current position: " + kafkaConsumer.position(partition));
	                    	kafkaConsumer.seek(partition, position);
	                    }
	                    logger.log(Level.WARN, "Producer can now begin producing messages.");
                	} catch(final Exception e) {
                		e.printStackTrace();
                	}
                	
                    canProduce = true;
                }
            });
        }

        @Override
        public void run() {
        		logger.log(Level.WARN, "Consumer is starting.");
	
	            while (!closing) {
	                try {
	                    // Poll on the Kafka consumer every second.
	                    Iterator<ConsumerRecord<byte[], byte[]>> it = kafkaConsumer
	                            .poll(1000).iterator();
	
	                    // Iterate through all the messages received and print their
	                    // content.
	                    // After a predefined number of messages has been received, the
	                    // client
	                    // will exit.
	                    while (it.hasNext()) {
	                        ConsumerRecord<byte[], byte[]> record = it.next();
	                        currentConsumedMessage = new String(record.value(),
	                                Charset.forName("UTF-8"));
	
	                        currentConsumedMessage = currentConsumedMessage+". Offset: " + record.offset();
	                        consumedMessages.add(currentConsumedMessage);
	                    }
	
	                    kafkaConsumer.commitSync();
	
	                    Thread.sleep(1000);
	                } catch (final InterruptedException e) {
	                	logger.log(Level.ERROR, "Producer/Consumer loop has been unexpectedly interrupted");
	                    shutdown();
	                } catch (final Exception e) {
	                	logger.log(Level.ERROR, "Consumer has failed with exception: " + e );
	                    shutdown();
	                }
	            }
	
	            logger.log(Level.WARN, "Consumer is shutting down.");
	            kafkaConsumer.close();
        }

        public void shutdown() {
            closing = true;
        }
    }
}
