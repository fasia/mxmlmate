package traversingXML;

	import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Random;

	import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.xerces.dom.NamedNodeMapImpl;
import org.evosuite.Properties;
import org.msgpack.io.StreamOutput;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.xmlmate.*;
import org.xmlmate.xml.ValueGenerator;










import com.sun.source.tree.NewClassTree;

import traversingXML.XMLUtil;
	public class Traverse {
		public static String path;
		
		public String getpath(){
			return path; 
		}
	   public static void traverse(File input, int d) throws TransformerException, ParserConfigurationException, SAXException, IOException, XPathExpressionException {
	      
	         //File inputFile = new File("desired.xml");
	         DocumentBuilderFactory dbFactory 
	            = DocumentBuilderFactory.newInstance();
	         DocumentBuilder dBuilder;

	         dBuilder = dbFactory.newDocumentBuilder();

	         Document doc = dBuilder.parse(input);
	         doc.getDocumentElement().normalize();

	         XPath xPath =  XPathFactory.newInstance().newXPath();
	         String exp2 = "//*";
	         mutateAddChild(xPath, doc, exp2);
	         //mutateDelChildren(xPath, doc, exp2);
	         //mutateElement(xPath, doc, exp2);
	        // mutateAttr(xPath, doc, exp2);
	        

	        
	        // NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(doc, XPathConstants.NODESET);
	         
	         /*for (int i = 0; i < nodeList.getLength(); i++) {
	            Node nNode = nodeList.item(i);
	            System.out.println("\nCurrent Element :" 
	               + nNode.getNodeName());
	            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
	               Element eElement = (Element) nNode;
//	               System.out.println("elemet attributes are : " 
//	                  + eElement.getAttributes());
	               System.out.println(" and its value is  : " 
	                  + eElement.getTextContent());
	            }*/

	         
	         
				//apply random selection of elements
	         /*int rnd = new Random().nextInt(nodeList.getLength());
	         Node nNode1 = nodeList.item(rnd);
	         System.out.println("randomly selected Element :" 
	 	               + nNode1.getNodeName());
	         if (nNode1.getNodeType()==Node.ELEMENT_NODE){
	        	 Element eElement = (Element) nNode1;
	        	 System.out.println(" and its value is  : " 
		                  + eElement.getTextContent());*/

	        	 
	         
	         //save the mutated file
    		 Source source = new DOMSource(doc);
    		 File file= new File("E:/Faezeh/xmlmate1/xmlmate/MutatedFiles/"+ d +".xml");
    		 path = file.getAbsolutePath();
    		 Result out = new StreamResult(file);
    		 TransformerFactory factory = TransformerFactory.newInstance();
    		 Transformer transformer = factory.newTransformer();
    		 transformer.transform(source, out);

    		 
/*    		 String path = file.getAbsolutePath();
    		 try{
    			 Class<?> cls = Properties.getTargetClass();
    	         Method main = cls.getMethod("main", String[].class);
    	         main.invoke(null, new Object[]{new String[]{path}});
    	         System.out.println("after main");
    		 }catch(InvocationTargetException | IllegalAccessException | IllegalArgumentException | NoSuchMethodException | SecurityException e){
    			 
    		 }
	         */
	}


		private static void mutateAddChild(XPath xPath, Document doc, String exp2) throws XPathExpressionException {
			NodeList nodeList = (NodeList) xPath.compile(exp2).evaluate(doc, XPathConstants.NODESET);	
	        for (int i = 0; i < nodeList.getLength(); i++) {
	        Node nNode = nodeList.item(i);
	        }
			//randomly pick a node from the node list
	        boolean mut =false;
	        while(!mut){
			int rnd = new Random().nextInt(nodeList.getLength());
	        Node nNode1 = nodeList.item(rnd);
	        // check its name, value 
	        System.out.println("randomly selected node :" 
		               + nNode1.getNodeName());
	
	        if (nNode1.getNodeType() == Node.ELEMENT_NODE){
	        	Element eElement = (Element) nNode1;
                System.out.println(eElement.getNodeName()+" first chid is = "+eElement.getChildNodes().getLength());

	        	if (eElement.hasChildNodes()){
	                System.out.println(eElement.getNodeName()+" first chid is = "+eElement.getFirstChild());
	                //Element el = (Element) eElement.getFirstChild();
	                Node newNode = nNode1.cloneNode(false);
	                doc.adoptNode(newNode);
	                doc.getDocumentElement().appendChild(newNode);
	                //eElement.appendChild(child);
	                //System.out.println(child.getNodeName());
	                System.out.println(eElement.getNodeName()+" number of children = "+eElement.getChildNodes().getLength());
	
		               // eElement.setTextContent(ValueGenerator.randomString());
		                //System.out.println(eElement.getNodeName()+" is now = "+eElement.getTextContent()+eElement.getNodeType());
		            mut=true;
	        	}
	        }//end if 
	        
	        }//end loop		
	}


		private static void mutateDelChildren(XPath xPath, Document doc, String exp2) throws XPathExpressionException {
			NodeList nodeList = (NodeList) xPath.compile(exp2).evaluate(doc, XPathConstants.NODESET);	
	        for (int i = 0; i < nodeList.getLength(); i++) {
	        Node nNode = nodeList.item(i);
	        }
			//randomly pick a node from the node list
	        boolean mut =false;
	        while(!mut){
			int rnd = new Random().nextInt(nodeList.getLength());
	        Node nNode1 = nodeList.item(rnd);
	        // check its name, value 
	        System.out.println("randomly selected node :" 
		               + nNode1.getNodeName());
	
	        if (nNode1.getNodeType() == Node.ELEMENT_NODE){
	        	Element eElement = (Element) nNode1;
                System.out.println(eElement.getNodeName()+" first chid is = "+eElement.getChildNodes().getLength());

	        	if (eElement.getChildNodes().getLength()!=0){
	                System.out.println(eElement.getNodeName()+" first chid is = "+eElement.getFirstChild());
	                eElement.removeChild(eElement.getFirstChild());
	                System.out.println(eElement.getNodeName()+" first chid is = "+eElement.getFirstChild());
	
		               // eElement.setTextContent(ValueGenerator.randomString());
		                //System.out.println(eElement.getNodeName()+" is now = "+eElement.getTextContent()+eElement.getNodeType());
		            mut=true;
	        	}
	        }//end if 
	        
	        }//end loop
	        		
	}


		private static void mutateElement(XPath xPath, Document doc, String exp2) throws XPathExpressionException {
			NodeList nodeList = (NodeList) xPath.compile(exp2).evaluate(doc, XPathConstants.NODESET);	
	        for (int i = 0; i < nodeList.getLength(); i++) {
	        Node nNode = nodeList.item(i);
	        }
			//randomly pick a node from the node list
	        boolean mut =false;
	        while(!mut){
			int rnd = new Random().nextInt(nodeList.getLength());
	        Node nNode1 = nodeList.item(rnd);
	        // check its name, value 
	        System.out.println("randomly selected node :" 
		               + nNode1.getNodeName());
	
	        if (nNode1.getNodeType() == Node.ELEMENT_NODE){
	        	
	        	Element eElement = (Element) nNode1;
	                System.out.println(eElement.getNodeName()+" = "+eElement.getTextContent());
	                eElement.setTextContent(ValueGenerator.randomString());
	                System.out.println(eElement.getNodeName()+" is now = "+eElement.getTextContent()+eElement.getNodeType());
	                mut=true;
	        }//end if 
	        
	        }//end loop
	        
			
		}


	private static void mutateAttr(XPath xPath, Document doc, String exp2) throws XPathExpressionException {
		//define the node list
		NodeList nodeList = (NodeList) xPath.compile(exp2).evaluate(doc, XPathConstants.NODESET);	
        for (int i = 0; i < nodeList.getLength(); i++) {
        Node nNode = nodeList.item(i);      
        }
		//randomly pick a node from the node list
        boolean mut =false;
        while(!mut){
		int rnd = new Random().nextInt(nodeList.getLength());
        Node nNode1 = nodeList.item(rnd);
        // check its name, value and attrs
        System.out.println("randomly selected node :" 
	               + nNode1.getNodeName());
        // define an element from the node
       // Element eElement = (Element) nNode1;
        //check whether it is an attribute
        if (nNode1.hasAttributes()){
        	NamedNodeMap attrs = nNode1.getAttributes();
        	int rnd2 =new Random().nextInt(attrs.getLength());
        	Attr attribute = (Attr) attrs.item(rnd2);
            System.out.println(attribute.getName()+" random attr value  = "+attribute.getValue());
            attribute.setNodeValue(ValueGenerator.randomString());
            System.out.println(attribute.getName()+" new value = "+attribute.getValue());

            mut=true;
        }//end if
        }//end loop
	}
}

