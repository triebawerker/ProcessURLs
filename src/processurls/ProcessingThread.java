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
    
    private static Logger logger = Logger.getRootLogger();
    
    Thread t = null;
    UUID guid = null;
    ArrayList<YategoImage> sourceData = null;
    LSHCeddExtractor ceddExtractor = new LSHCeddExtractor();
    LSHGaborExtractor gaborExtractor = new LSHGaborExtractor();
    String basePath = null;
    String redisConnectionString = null;
    Jedis jedis = null;
    
    static Boolean accessFlag = false;
    
    ProcessingThread(ArrayList<YategoImage> mySourceData, String myBasePath, String myRedisConnectionString) {

        guid = UUID.randomUUID();
        sourceData = mySourceData;
        basePath = myBasePath;
        redisConnectionString = myRedisConnectionString;
        
        jedis = new Jedis(redisConnectionString);
        
        t = new Thread(this, guid.toString());
        logger.debug("Creating child thread: " + t);
        t.start();

    }
   
    private void processURL(YategoImage sourceData, String basePath) {
        
        logger.debug("Processing: " + sourceData.imageURL + "...");
        
        try {
        
            ImageInImageSpace img = new ImageInImageSpace();

            img.initializeImageFromURL(sourceData.imageURL);           
            
            img.setExtractor(ceddExtractor);
            img.saveImageMetaDataToFile(basePath + "iisCEDD/" + img.getUUID().toString() + ".iis");            

            img.setExtractor(gaborExtractor);
            img.saveImageMetaDataToFile(basePath + "iisGabor/" + img.getUUID().toString() + ".iis");

            sourceData.ceddFeatureVector = img.getCeddDescriptorVectorDouble();
            sourceData.fcthFeatureVector = img.getFCTHDescriptorVectorDouble();
            sourceData.gaborFeatureVector = img.getGaborDescriptorVectorDouble();
            sourceData.tamuraFeatureVector = img.getTamuraDescriptorVectorDouble();
            
            ObjectMapper mapper = new ObjectMapper();

            jedis.hset("images", img.getUUID().toString(), mapper.writeValueAsString(sourceData));

        } catch (Exception e) {
            
            logger.error("Processing " + sourceData.imageURL + " failed with exception: " + e.getMessage());
            
        }
  
   }

   public void run() {
     
     while (!sourceData.isEmpty()) {
     
         YategoImage nextImage = null;
         
         synchronized(ProcessingThread.class) {
            
             if(accessFlag == false) {

               accessFlag = true;
               nextImage = sourceData.get(0);
               sourceData.remove(0);
               accessFlag = false;

            }
         
         }
         
         if (nextImage != null) {
            processURL(nextImage, basePath);
         }
         
     }
     
     if (ProcessURLs.saveMethod == ProcessURLs.SAVE_TO_REDIS) {
            
        jedis.disconnect();
            
     }
     
     logger.debug("Exiting child thread.");
   
   }
   
}
