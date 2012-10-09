/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package processurls;

import org.apache.log4j.*;

import redis.clients.jedis.*;

import org.codehaus.jackson.*;

import org.codehaus.jackson.map.*;

import de.mayflower.lsh.ImageInImageSpace;
import de.mayflower.lsh.LSHCeddExtractor;
import de.mayflower.lsh.LSHGaborExtractor;
import java.util.ArrayList;
import java.util.UUID;

/**
 *
 * @author alexander.oldemeier
 */
class ProcessingThread implements Runnable {
   
    class ImageInRedis {
        
        public String url; 
        public double[] ceddFeatures;
        public double[] gaborFeatures;
        
    }
    
    private static Logger logger = Logger.getRootLogger();
    
    Thread t = null;
    UUID guid = null;
    ArrayList<String> URLs = null;
    LSHCeddExtractor ceddExtractor = new LSHCeddExtractor();
    LSHGaborExtractor gaborExtractor = new LSHGaborExtractor();
    String basePath = null;
    String redisConnectionString = null;
    Jedis jedis = null;
    
    static Boolean accessFlag = false;
    
    ProcessingThread(ArrayList<String> myURLs, String myBasePath, String myRedisConnectionString) {

        guid = UUID.randomUUID();
        URLs = myURLs;
        basePath = myBasePath;
        redisConnectionString = myRedisConnectionString;
        
        jedis = new Jedis(redisConnectionString);
        
        t = new Thread(this, guid.toString());
        logger.debug("Creating child thread: " + t);
        t.start();

    }
   
    private void processURL(String url, String basePath) {
        
        logger.debug("Processing: " + url + "...");
        
        try {
        
            ImageInImageSpace img = new ImageInImageSpace();

            img.initializeImageFromURL(url);
            
            
            img.setExtractor(ceddExtractor);
            img.saveImageMetaDataToFile(basePath + "iisCEDD/" + img.getUUID().toString() + ".iis");            

            img.setExtractor(gaborExtractor);
            img.saveImageMetaDataToFile(basePath + "iisGabor/" + img.getUUID().toString() + ".iis");


            ImageInRedis myImage = new ImageInRedis();

            myImage.url = img.getURL();
            myImage.ceddFeatures = img.getCeddDescriptorVectorDouble();
            myImage.gaborFeatures = img.getGaborDescriptorVectorDouble();

            ObjectMapper mapper = new ObjectMapper();

            jedis.hset("images", img.getUUID().toString(), mapper.writeValueAsString(myImage));

        } catch (Exception e) {
            
            logger.error("Processing " + url + " failed with exception: " + e.getMessage());
            
        }
  
   }

   public void run() {
     
     while (!URLs.isEmpty()) {
     
         String nextURL = null;
         
         synchronized(ProcessingThread.class) {
            
             if(accessFlag == false) {

               accessFlag = true;
               nextURL = URLs.get(0);
               URLs.remove(0);
               accessFlag = false;

            }
         
         }
         
         if (nextURL != null) {
            processURL(nextURL, basePath);
         }
         
     }
     
     if (ProcessURLs.saveMethod == ProcessURLs.SAVE_TO_REDIS) {
            
        jedis.disconnect();
            
     }
     
     logger.debug("Exiting child thread.");
   
   }
   
}
