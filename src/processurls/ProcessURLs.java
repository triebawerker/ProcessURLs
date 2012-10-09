/*
 * Extract features from a file containing a list of URLs.
 */
package processurls;

import com.mysql.jdbc.PreparedStatement;
import de.mayflower.lsh.*;

import org.apache.log4j.*;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

/**
 *
 * @author Alex Oldemeier
 */
public class ProcessURLs {

    private static Logger logger = Logger.getRootLogger();

    static long startTime = -1;
    static final int NUM_THREADS = 10;

    static final int SAVE_TO_FILESYSTEM = 1;
    static final int SAVE_TO_REDIS = 2;

    static int saveMethod = SAVE_TO_FILESYSTEM;

    private static void startMeasureTime() {
        startTime = System.currentTimeMillis();
    }

    private static void printElapsedTime() {

        if (startTime == -1) {
            logger.error("Time has not been measured.");
        }
        else {

            long stopTime = System.currentTimeMillis();

            logger.info("Elapsed time: "  + Long.toString(stopTime-startTime) + " milliseconds...");

            startTime = -1;

        }

    }

    private static void processURLs(ArrayList<YategoImage> images, String basePath, String redisConnectionString) {

        logger.info("Now processing " + images.size() + " images...");

        ProcessingThread[] processingThreads = new ProcessingThread[NUM_THREADS];

        for (int i=0;i<NUM_THREADS;i++) {

            processingThreads[i] = new ProcessingThread(images, basePath, redisConnectionString);

        }

        try {

         while(!images.isEmpty()) {

           Thread.sleep(1000);

         }
      } catch (InterruptedException e) {

         logger.error("Main thread interrupted. Exception: " + e.getMessage());

      }

    }

    private static void initLogger() {

      try {

        SimpleLayout layout = new SimpleLayout();
        ConsoleAppender consoleAppender = new ConsoleAppender( layout );
        logger.addAppender( consoleAppender );
        FileAppender fileAppender = new FileAppender( layout, "logFile.log", false );
        logger.addAppender( fileAppender );
        // ALL | DEBUG | INFO | WARN | ERROR | FATAL | OFF:
        logger.setLevel( Level.ALL );

      } catch (Exception e) {

          System.err.println("Exception during logger init: " + e.getMessage());

      }

    }

    private static ArrayList<YategoImage> getURLArrayListFromFileSource(String fileName) throws IOException {

        ArrayList<YategoImage> retval = new ArrayList();

        FileInputStream fstream = new FileInputStream(fileName);
        DataInputStream in = new DataInputStream(fstream);
        BufferedReader br = new BufferedReader(new InputStreamReader(in));

        String strLine = null;
        int i = 0;

        while ((strLine = br.readLine()) != null) {

            if (strLine.startsWith("http://")
                    && (strLine.endsWith(".jpg") || strLine.endsWith(".JPG") || strLine.endsWith(".jpeg")
                    || strLine.endsWith(".png") || strLine.endsWith(".gif"))) {

                YategoImage image = new YategoImage();
                image.imageURL = strLine;

                retval.add(image);

            } else {
                //System.out.println("Not processing line because it is not a URL.");
            }
            i++;

        }

        in.close();

        return retval;

    }

    private static ArrayList<YategoImage> getURLArrayListFromSQLSource(String connectionString) throws SQLException {

        Connection connect = null;
        Statement statement = null;
        ResultSet resultSet = null;
        ArrayList<YategoImage> retval = new ArrayList();

        try {

            Class.forName("com.mysql.jdbc.Driver");
            connect = DriverManager.getConnection(connectionString);

            statement = connect.createStatement();

            resultSet = statement.executeQuery("SELECT picture,article_url,"
                    + "category_level_one,category_level_two,category_level_three"
                    + " FROM yatego.product_feed_articles");

            ArrayList<String> catOne = new ArrayList();

            while (resultSet.next()) {

              YategoImage image = new YategoImage();

              image.imageURL = resultSet.getString("picture");
              image.productURL = resultSet.getString("article_url");
              image.category_level_one = resultSet.getString("category_level_one");
              image.category_level_two = resultSet.getString("category_level_two");
              image.category_level_three = resultSet.getString("category_level_three");

              retval.add(image);

            }
        } catch (Exception e) {

            logger.error("Exception while reading from SQL database: " + e.getMessage());

        } finally {

            connect.close();
            statement.close();
            resultSet.close();

        }

        return retval;

    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        if (args.length != 3) {
            System.out.println("processurls url_file_path processed_images_path redis_connection_string");
            return;
        }

        String sourceName = args[0];
        String basePath = args[1];
        String redisConnectionString = args[2];

        initLogger();

        logger.info("Processing URLs from: " + sourceName);

        try{

            logger.info("Extracting URLs...");

            startMeasureTime();

            ArrayList<YategoImage> imageSourceData = new ArrayList();

            if (sourceName.startsWith("jdbc:mysql:")) {

                imageSourceData = getURLArrayListFromSQLSource(sourceName);

            } else {

                imageSourceData = getURLArrayListFromFileSource(sourceName);

            }

            printElapsedTime();

            startMeasureTime();

            processURLs(imageSourceData, basePath, redisConnectionString);

            printElapsedTime();

        }catch (Exception e){

            logger.error("Exception in main: " + e.getMessage());

        }

    }
}
