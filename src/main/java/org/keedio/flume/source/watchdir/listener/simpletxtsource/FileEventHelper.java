package org.keedio.flume.source.watchdir.listener.simpletxtsource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.LineNumberReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.flume.Event;
import org.apache.flume.event.EventBuilder;
import org.keedio.flume.source.watchdir.FileUtil;
import org.keedio.flume.source.watchdir.WatchDirEvent;
import org.keedio.flume.source.watchdir.metrics.MetricsEvent;
import org.keedio.flume.source.watchdir.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This worker proccess the xml file in order to extract the expeted events.
 * @author rolmo
 *
 */
public class FileEventHelper {

	private static final Logger LOGGER = LoggerFactory
			.getLogger(FileEventHelper.class);

	FileEventSourceListener listener;
	String path;
	Long offset;
	
	public FileEventHelper(FileEventSourceListener listener, String path, Long offset) {
		this.listener = listener;
		this.path = path;
		this.offset = offset;
	}
	
	public Long launchEvents() throws Exception {
		try {
			Date inicio = new Date();
			int procesados = 0;

			Long newOffset = readLines(path, offset);

			long intervalo = new Date().getTime() - inicio.getTime();

			// Notificamos el tiempo de procesado para las metricas
			listener.getMetricsController().manage(new MetricsEvent(MetricsEvent.MEAN_FILE_PROCESS, intervalo));
			listener.getMetricsController().manage(new MetricsEvent(MetricsEvent.TOTAL_FILE_EVENTS, procesados));

			return newOffset;

		} catch (Exception e) {
			LOGGER.error("Error procesando el fichero: " + path);
			LOGGER.error(e.getMessage());
			LOGGER.error(e.getStackTrace());

			throw e;
		}
	}

	private Long readLines(String path, Long offset) throws Exception {

		BufferedReader lReader = new BufferedReader(new FileReader(new File(path)));

		lReader.skip(offset);
		Long newOffset = 0L;

		try {
			newOffset = offset;
			int lines = 0;
			String line;
			List<Event> events = new ArrayList<Event>();
			while ((line = lReader.readLine())!=null) {
				Event ev = EventBuilder.withBody(line.getBytes());
				
				// Put header props
				Map<String,String> headers = new HashMap<String, String>();
				if (listener.fileHeader)
					headers.put(listener.fileHeaderName, path);
				if (listener.baseHeader)
					headers.put(listener.baseHeaderName, path);
				if (!headers.isEmpty())
					ev.setHeaders(headers);				
				
	    		// Calls to getChannelProccesor are synchronyzed
				events.add(ev);
	            lines ++;
				newOffset =  newOffset + line.length() + 1;
	            
	    		// Notificamos un evento de nuevo mensaje
	    		listener.getMetricsController().manage(new MetricsEvent(MetricsEvent.NEW_EVENT));
	    		
			}

			listener.getChannelProcessor().processEventBatch(events);

			return newOffset;

		} catch (IOException e) {
			LOGGER.error("Error al procesar el fichero: " + path, e);
			throw e;
		} finally {
			lReader.close();
		}
	}

	public int countLines(String filename) throws IOException {
	    LineNumberReader reader  = new LineNumberReader(new FileReader(filename));
	
	    int cnt = 0;

	    reader.skip(Long.MAX_VALUE);

	    cnt = reader.getLineNumber(); 
	    reader.close();
	    
	    return cnt;
	}


	public long getBytesSize(String filename)
	{
        File url = null;

        url = new File(filename);
            
        return url.length();

    }

}
