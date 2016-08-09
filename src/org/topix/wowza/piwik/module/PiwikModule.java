package org.topix.wowza.piwik.module;



import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
//import java.net.URL;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.core.UriBuilder;

import org.apache.http.HttpResponse;
import org.piwik.java.tracking.PiwikDate;
import org.piwik.java.tracking.PiwikRequest;
import org.piwik.java.tracking.PiwikTracker;
import org.topix.wowza.piwik.PiwikListener;

import com.wowza.util.StringUtils;
import com.wowza.wms.amf.AMFPacket;
import com.wowza.wms.application.IApplication;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.application.WMSProperties;
import com.wowza.wms.client.IClient;
import com.wowza.wms.httpstreamer.model.IHTTPStreamerSession;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.mediacaster.IMediaCaster;
import com.wowza.wms.mediacaster.MediaCasterNotifyBase;
import com.wowza.wms.module.ModuleBase;
import com.wowza.wms.stream.IMediaStream;
import com.wowza.wms.stream.IMediaStreamActionNotify2;
import com.wowza.wms.vhost.IVHost;
import com.wowza.wms.rtp.model.IRTSPActionNotify;
import com.wowza.wms.rtp.model.RTPSession;
import com.wowza.wms.rtp.model.RTPStream;
import com.wowza.wms.rtsp.RTSPRequestMessage;
import com.wowza.wms.rtsp.RTSPResponseMessages;


public class PiwikModule extends ModuleBase {


	// this class implements the actual task that connects to the piwik API
	class LoggerWorker implements Runnable {
		private final int RETRIES=1;
		private int sleepMsec=600;
		private PiwikTracker tracker;

		// private boolean debug = true;
		private long connectTimeMSec;
		private String fullStreamPath;
		private String sessionId;
		private String protocol;
		private String fullURI;
		private String ipaddr;
		private long dataoutBytes;
		private long runningSec;
		private String userAgent;
		private String referrer;
		private WMSLogger logger;
		
		
		public LoggerWorker(WMSLogger logger, long timestamp, String loggedUrl, String sId,
				String prot, String fullURI, String ip, long dataout, long running,
				String userAg, String ref) {
			
	        this.logger = logger;

	        this.connectTimeMSec=timestamp;
	        this.fullStreamPath=loggedUrl;
	        this.sessionId=sId;
	        this.protocol=prot;
			this.fullURI = fullURI;
			this.ipaddr=ip;
			this.dataoutBytes=dataout;
			this.runningSec=running;
			this.userAgent=userAg;
			this.referrer=ref;
			
			try {
				if (debug)
					logger.info("piwiklogger: going to instantiate piwik tracker");
				
				tracker = new PiwikTracker(piwikApiEndpoint);
				
				
			} catch (Exception e) {
				logger.error("piwiklogger: cannot instantiate piwik tracker", e);
			}

			
		}

		
		@Override
		public void run() {
			int count = 0;
			while (true)
			{
				String[] splitURI=fullURI.split("/");
				//String uriProtocol=splitURI[0];
				// splitURI[2] is in the form <address>:<port>
				String serverAddress=splitURI[2].split(":")[0];

				// String fullStreamURI=splitURI[0]+"://"+splitURI[2]+"/"+fullStreamPath;
				String fullStreamURI = "rtmp://wowzaservers/" + fullStreamPath;
				
				if (debug)
					this.logger.info("piwiklogger going to send new log data: started: " + connectTimeMSec +
							" stream " + fullStreamPath +
							" session ID: " + sessionId + 
							" protocol: " + protocol +
							" client IP: " + ipaddr +
							" server addr: " + serverAddress +
							" bytes out: " + dataoutBytes +
							" running time sec: " + runningSec +
							" user agent: " + userAgent + 
							" referrer: " + referrer +
							" piwik API endpoint: " + piwikApiEndpoint +
							" piwik key: " + piwikKey +
							" piwik website ID: " + piwikWebsiteId							
							);
				
				
				
				PiwikRequest request = null;
				try {
					request = new PiwikRequest(new Integer(piwikWebsiteId), null);
					request.setActionUrlWithString(fullStreamURI);
					request.setAuthToken(piwikKey);
					request.setRequestDatetime(new PiwikDate(connectTimeMSec) );
					request.setVisitorIp(ipaddr);
					request.setReferrerUrlWithString(referrer);
					// request.setReferrerUrl(referrer);
					
					request.setContentName("/"+fullStreamPath);
					request.setContentPiece(fullURI);
					request.setHeaderUserAgent(userAgent);
					request.setDownloadUrlWithString(fullStreamURI);
					// request.setDownloadUrl(fullStreamURI);
					
					// Custom dimensions plugin needs to be installed and
					// custom Action Dimensions 3 and 4 must be enabled on Piwik server
					request.setCustomTrackingParameter("dimension3", protocol);
					request.setCustomTrackingParameter("dimension4", serverAddress);
					
					request.setCustomTrackingParameter("bw_bytes", String.valueOf(dataoutBytes));
					// request.setCustomTrackingParameter("session_id", sessionId);
					request.setPageCustomVariable("session_id", sessionId);
					request.setPageCustomVariable("playtime_sec", String.valueOf(runningSec));
					request.setActionTime(runningSec*1000);
					
					if (debug)
						logger.info("piwiklogger: request API call is [" + request.getUrlEncodedQueryString() + "]");
					
				} catch (Exception e) {
					logger.error("piwiklogger: exception setting up piwik request", e);

				}
				
				boolean result = false;
				if (request != null) {
					try {
						HttpResponse response = tracker.sendRequest(request);
						int code = response.getStatusLine().getStatusCode();
						if ( code >= 200 && code <= 399 ) {
							result = true;
						} else {
							this.logger.error("piwiklogger: problem connecting to " + piwikApiEndpoint + " HTTP response " + response.getStatusLine().getReasonPhrase());

						}
					} catch (Exception e) {
						this.logger.error("piwiklogger: problem connecting to " + piwikApiEndpoint, e);
					}

				}
				
				

				if (result != false) {
					if (debug)
						this.logger.info("piwiklogger: Success! Logged data to Piwik");
					break;
				} else {
					count++;
					if (count >= RETRIES) {
						if (debug)
							this.logger.info("piwiklogger: Failed logging data to Piwik");
						break;
					}

					try {

						Thread.sleep(sleepMsec);
						// back-off factor of 2
						sleepMsec=sleepMsec*2;
					} catch (InterruptedException e) {
						if (debug)
							this.logger.info("piwiklogger: thread InterruptedException", e);

					}
				}
			}
			
			
			if (debug)
				this.logger.info("piwiklogger: thread finished");
		}
	}

	
	
	
	
	class RtmpNotifier implements IMediaStreamActionNotify2 {

		@Override
		public void onPause(IMediaStream stream, boolean isPause, double location) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onPlay(IMediaStream stream, String streamName,
				double playStart, double playLen, int playReset) {
			IClient client = stream.getClient();
			if (client != null) {
				if (debug)
					logger.info("piwikrtmp: start playing RTMP stream " + streamName);
			}
			
			// track session ID in connection map
			String sessionId=String.valueOf(stream.getClientId());
			// setConnectionMap(sessionId);

		}

		@Override
		public void onPublish(IMediaStream stream, String streamName,
				boolean isRecord, boolean isAppend) {
			if (debug)
				logger.info("piwikrtmp: start publishing RTMP stream " + streamName);

		}

		@Override
		public void onSeek(IMediaStream stream, double location) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onStop(IMediaStream stream) {
			IClient client = stream.getClient();
			if (client != null) {
				
				if (client.isLiveRepeater()) {
					if (debug)
						logger.info("piwikrtmp: stop playing RTMP stream " + stream.getName() + " (live repeater)");
				} else {
					String streamName=stream.getName();
					if (debug)
						logger.info("piwikrtmp: stop playing RTMP stream " + streamName);
					long dataoutBytes = client.getTotalIOPerformanceCounter().getMessagesOutBytes();
					long runningSec = (long)stream.getElapsedTime().getTimeSeconds();

					boolean tunnelled = false;
					String protocol = "RTMP";
					if (client.getProtocol() == 3) {
						tunnelled=true;
						protocol = "RTMPT";
					}
					
					if (client.isEncrypted()) {
						protocol = "RTMPE";
						if (tunnelled) {
							protocol = "RTMPTE";
						}
					}
					if (client.isSSL()) {
						protocol = "RTMPS";
					}
					
					// URI for RTMP clients is in the form rtmp://<server_ip>:<port>/<app>
					String uri=client.getUri() + "/" + streamName;
					
					
					sendLogDataToPiwik(client.getConnectTime(), client.getAppInstance(), stream.getName(), 
							String.valueOf(client.getClientId()), protocol, uri,
							client.getIp(), dataoutBytes, runningSec);
					
				}
			}
		}

		@Override
		public void onUnPublish(IMediaStream stream, String streamName,
				boolean isRecord, boolean isAppend) {
			if (debug)
				logger.info("piwikrtmp: stop publishing stream " + streamName);

		}

		@Override
		public void onMetaData(IMediaStream stream, AMFPacket metaDataPacket) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onPauseRaw(IMediaStream stream, boolean isPause, double location) {
			// TODO Auto-generated method stub

		}

	}
	
	
	class RtspNotifier implements IRTSPActionNotify {

		@Override
		public void onAnnounce(RTPSession rtspSession, RTSPRequestMessage req,
				RTSPResponseMessages resp) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onDescribe(RTPSession rtspSession, RTSPRequestMessage req,
				RTSPResponseMessages resp) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onGetParameter(RTPSession rtspSession, RTSPRequestMessage req,
				RTSPResponseMessages resp) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onOptions(RTPSession rtspSession, RTSPRequestMessage req,
				RTSPResponseMessages resp) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onPause(RTPSession rtspSession, RTSPRequestMessage req,
				RTSPResponseMessages resp) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onPlay(RTPSession rtspSession, RTSPRequestMessage req,
				RTSPResponseMessages resp) {
			String streamName="";
			String[] uriArray = rtspSession.getUri().split("/");
			if (uriArray != null && uriArray.length > 0)
			{
				streamName = uriArray[uriArray.length - 1];
			}
			if (debug)
				logger.info("piwikrtsp: start playing RTSP stream " + streamName);
			
			// track session ID in connection map
			String sessionId=rtspSession.getSessionId();
			// setConnectionMap(sessionId);


		}

		@Override
		public void onRecord(RTPSession rtspSession, RTSPRequestMessage req,
				RTSPResponseMessages resp) {

			// TODO Auto-generated method stub

		}

		@Override
		public void onRedirect(RTPSession rtspSession, RTSPRequestMessage req,
				RTSPResponseMessages resp) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onSetParameter(RTPSession rtspSession, RTSPRequestMessage req,
				RTSPResponseMessages resp) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onSetup(RTPSession rtspSession, RTSPRequestMessage req,
				RTSPResponseMessages resp) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onTeardown(RTPSession rtspSession, RTSPRequestMessage req,
				RTSPResponseMessages resp) {

			String streamName="";
			RTPStream stream = rtspSession.getRTSPStream();
			if (stream != null && stream.isModePublish() == false) {
				String[] uriArray = rtspSession.getUri().split("/");
				if (uriArray != null && uriArray.length > 0)
				{
					streamName = uriArray[uriArray.length - 1];
				}
				if (debug)
					logger.info("piwikrtsp: stop playing RTSP stream " + streamName);
				
				if (streamName != "") {
					long dataoutBytes = rtspSession.getIOPerformanceCounter().getMessagesOutBytes();
					long runningSec = (long)rtspSession.getElapsedTime().getTimeSeconds();

					Date now=new Date();
					long connectTime = now.getTime() - (long)runningSec * 1000;
					
					// URI for RTSP sessions is in the form rtsp://<server_ip>:<port>/<app>/<stream>
					String uri=rtspSession.getUri();


					sendLogDataToPiwik(connectTime, rtspSession.getAppInstance(), streamName, 
							rtspSession.getSessionId(), "RTSP", uri,
							rtspSession.getIp(), dataoutBytes, runningSec, rtspSession.getUserAgent(), rtspSession.getReferrer());

					
					
				}
			}
		}

	}

	
	class MediacasterNotifier extends MediaCasterNotifyBase {

	    
	    @Override
		public void onStreamStart(IMediaCaster mediaCaster)
		{
	    	IMediaStream stream = mediaCaster.getStream();
	    	String streamName = stream.getName();
	    	if (debug)
	    		logger.info("piwikmediacaster: start publishing mediacaster stream " + streamName);
		}

		@Override
		public void onStreamStop(IMediaCaster mediaCaster)
		{
	    	IMediaStream stream = mediaCaster.getStream();
	    	String streamName = stream.getName();
	    	if (debug)
	    		logger.info("piwikmediacaster: stop publishing mediacaster stream " + streamName);
		}

	}


	
	
	

	private RtmpNotifier rtmpNotifier = new RtmpNotifier();
	private RtspNotifier rtspNotifier = new RtspNotifier();
	private MediacasterNotifier mediacasterNotifier = new MediacasterNotifier();
	// private LoggerThreadPool piwikLogger = null;

	private String piwikKey;
	private String piwikApiEndpoint;
	private String piwikWebsiteId;
	private boolean isInitialized = false;
	private boolean debug = false;
	
	private HashMap <String, long[]> connectionMap;

	private static WMSLogger logger = null;
	private static ThreadPoolExecutor logThreadPool;

	static {
        logger = WMSLogger.getLogger("PiwikModule");

			
		try {
			logger.info("piwikmodule: starting log thread pool");
			logThreadPool = new ThreadPoolExecutor(
					5, // core size
					10, // max size
					30, // idle timeout seconds
					TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
		} catch (Exception e) {
			logger.error("piwikmodule: cannot instantiate log thread pool", e);
		}

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				try {
					logger.info("piwikmodule: shutting down logThread pool");
					logThreadPool.shutdown();
					if (!logThreadPool.awaitTermination(3, TimeUnit.SECONDS))
						logThreadPool.shutdownNow();
				} catch (InterruptedException e) {
					logger.info("piwikmodule: forcefully shutting down logThreads", e);
				}
			}
		});

	}

	
	public PiwikModule() {
		this.connectionMap = new HashMap<String, long[]>();
	}
    
    
	
	// this method is to be called when a connection starts and it is needed to keep track of
	// several play/stop events for the same Wowza session ID
	// (eg. for HTTP streams that have a timeout of about 20sec that allows this condition to be detected)
	// this method maintains an hashmap of active sessions, for each session the map contains an array of int containing:
	//		0 - session counter
	//		1 - datatransferred counter
	//		2 - time counter
	// if connectionMap does not contain an entry for the specified session ID then a new array is added
	// if connectionMap already contains an entry for the specified session ID then the corresponding counter is incremented
	private synchronized void setConnectionMap(String sessionId) {
		long[] curSessionEntry=this.connectionMap.get(sessionId);
		if (curSessionEntry == null) {
			// create a new entry
			curSessionEntry=new long[3];
			curSessionEntry[0]=0;
			curSessionEntry[1]=0;
			curSessionEntry[2]=0;
		}
		curSessionEntry[0]++;
		
		this.connectionMap.put(sessionId, curSessionEntry);
	}

	
	// this method is to be called when a connection is closed and it is needed to keep track of several play/stop events for the same Wowza session ID (eg. for HTTP streams)
	// this method returns the number of active connections for the specified session with their cumulative additional data transferred and playtime
	// returns an array of 3 int:
	// [0] connection counter (when this counter is 0 there are no more active connections for the specified session ID)
	// [1] data transferred
	// [2] playtime
	private synchronized long[] getConnectionMap(String sessionId, long datatransferred, long playtime) {
		long[] curSessionEntry=this.connectionMap.get(sessionId);
		if (curSessionEntry == null) {
			// create a new entry
			curSessionEntry=new long[3];
			curSessionEntry[0]=0;
			curSessionEntry[1]=0;
			curSessionEntry[2]=0;
		} else {
			if (curSessionEntry[0] > 0) {
				curSessionEntry[0]--;
			}
		}
		curSessionEntry[1] += datatransferred;
		curSessionEntry[2] += playtime;
		
		if (curSessionEntry[0] > 0) {
			// there are other active streams with the same session ID
			// update the connection map
			this.connectionMap.put(sessionId, curSessionEntry);
		} else {
			// no more streams with the same session ID
			// remove the entry in the connection map
			this.connectionMap.remove(sessionId);
		}
		
		return curSessionEntry;
	}
	
	
    
    
	// lazy intialization of connection parameters for logging to Piwik
	private void lazyConnectionInit(String url, String key, String websiteId) {

		if (!StringUtils.isEmpty(url) && !StringUtils.isEmpty(key) && websiteId != "0") {
			if (debug)
				logger.info("piwikmodule: lazyConnectionInit, parameters good");

			this.piwikApiEndpoint = url + "/piwik.php";
			this.piwikKey = key;
			this.piwikWebsiteId=websiteId;
			this.isInitialized=true;

		}
	}
	

	
	private void sendLogDataToPiwik(long connectTimeMsec, IApplicationInstance appInstance, String streamname, 
			String sessionId, String protocol, String fullURI,
			String ipaddr, long dataoutBytes, long runningSec) {
		
		sendLogDataToPiwik(connectTimeMsec, appInstance, streamname, sessionId, protocol, fullURI, ipaddr, dataoutBytes, runningSec, "", "");
	}
	
	private void sendLogDataToPiwik(long connectTimeMsec, IApplicationInstance appInstance, String streamname, 
			String sessionId, String protocol, String fullURI,
			String ipaddr, long dataoutBytes, long runningSec, String userAgent, String referrer) {
		
		// check if there is an active connection for the same session ID
		// if there is already an active connection then add the datatransfer and playtime counters to the connection map
		// if there is no active connection for the same session ID get the total info and send data to piwik
		// long[] consolidatedData = getConnectionMap(sessionId, dataoutBytes, runningSec);
		long[] consolidatedData;
		consolidatedData[0] = 0;
		
		if (debug)
			logger.info("piwikmodule: got connectionmap for session ID " + sessionId + ", conn count=" + consolidatedData[0] + " data=" + consolidatedData[1] + " time=" + consolidatedData[2]);
	
		
		if (consolidatedData[0] > 0) {
			// there are other active connection with the same session ID
			// in this case the getConnectionMap method above updated the data and time counters
			// nothing to notify to piwik, data will be sent when the last connection is closed
			if (debug)
				logger.info("piwikmodule: there are other connections open for session ID " + sessionId + ", delaying piwik update");
		} else {
			// there are no other connections for the current session ID
			// update datatransferred and playtime using the consolidated data and send data to piwik
			// dataoutBytes=consolidatedData[1];
			// runningSec=consolidatedData[2];
			
			if (dataoutBytes > 0) {
				
				IApplication app = appInstance.getApplication();
				if (!this.isInitialized) {
					String[] piwikInfo = getPiwikConnectionParameters(app);
					this.lazyConnectionInit(piwikInfo[0], piwikInfo[1], piwikInfo[2]);
					

				}

				IVHost vhost = app.getVHost();
				String fullStreamPath = vhost.getName() +"/"+ app.getName() +"/"+ appInstance.getName() +"/"+ streamname;
				
				try {
					logThreadPool.execute(new LoggerWorker(logger, connectTimeMsec, fullStreamPath, sessionId, protocol, fullURI, ipaddr, dataoutBytes, runningSec, userAgent, referrer));
					if (debug)
						logger.info("piwikmodule: logger worker submitted");
				} catch(RejectedExecutionException e) {
					logger.error("piwikmodule: failed submitting logger worker!!!!!");
					
				}
			}
		}
	}

	
	

	public void onAppStart(IApplicationInstance appInstance)
	{
		appInstance.addMediaCasterListener(this.mediacasterNotifier);
		this.debug=appInstance.getApplication().getProperties().getPropertyBoolean(PiwikListener.PROP_PREFIX + "Debug", false);
		if (this.debug)
			logger.info("piwikmodule: debug enabled for app " + appInstance.getApplication().getName());
		else
			logger.info("piwikmodule: debug disabled for app " + appInstance.getApplication().getName());

		
	}
	public void onStreamCreate(IMediaStream stream)
	{
		stream.addClientListener(this.rtmpNotifier);
	}

	public void onStreamDestroy(IMediaStream stream)
	{
		stream.removeClientListener(this.rtmpNotifier);
	}

	public void onRTPSessionCreate(RTPSession rtpSession)
	{
		rtpSession.addActionListener(this.rtspNotifier);
	}

	public void onRTPSessionDestroy(RTPSession rtpSession)
	{
		rtpSession.removeActionListener(this.rtspNotifier);
	}

	public void onHTTPSessionCreate(IHTTPStreamerSession httpSession)
	{
		String streamName = httpSession.getStreamName();
		if (debug)
			logger.info("piwikmodule: started playing HTTP stream " + streamName);

	}
	public void onHTTPSessionDestroy(IHTTPStreamerSession httpSession)
	{
		String streamName = httpSession.getStreamName();
		if (debug)
			logger.info("piwikmodule: stopped playing HTTP stream " + streamName);
		
		long dataoutBytes = httpSession.getIOPerformanceCounter().getMessagesOutBytes();
		long runningSec = (long)httpSession.getElapsedTime().getTimeSeconds();
		
		Date now=new Date();
		long connectTime = now.getTime() - (long)runningSec * 1000;
		
		/*
		// actual running time should account for a timeout of about 20sec
		if (runningSec > 20) {
			runningSec=runningSec - 20;
		} else {
			runningSec=1;
		}
		*/
		
		// URI for HTTP sessions is in the form <app>/<stream>/<resource_file>
		String uri="http://" + httpSession.getServerIp() + ":" + httpSession.getServerPort() +"/" + httpSession.getUri();

		
		sendLogDataToPiwik(connectTime, httpSession.getAppInstance(), streamName, 
				httpSession.getSessionId(), getHTTPProtocol(httpSession), uri,
				httpSession.getIpAddress(), dataoutBytes, runningSec, httpSession.getUserAgent(), httpSession.getReferrer());
		
	}
	
	
	
	
	
	public static String[] getPiwikConnectionParameters(IApplication app) {
		WMSProperties props = app.getProperties();
		return new String[]{ 
				props.getPropertyStr(PiwikListener.PROP_PREFIX + "Url", ""),
				props.getPropertyStr(PiwikListener.PROP_PREFIX + "Key", ""),
				String.valueOf(props.getPropertyInt(PiwikListener.PROP_PREFIX + "WebsiteId", 0))

		};
		
	}
		
	
	public static String getHTTPProtocol(IHTTPStreamerSession session) {
		String prot = "HTTP";
		switch (session.getSessionProtocol()) {
		case IHTTPStreamerSession.SESSIONPROTOCOL_CUPERTINOSTREAMING:
			prot = "HTTPCupertino";
			break;
		case IHTTPStreamerSession.SESSIONPROTOCOL_DIRECTSTREAMING:
			prot = "HTTPDirect";
			break;
		case IHTTPStreamerSession.SESSIONPROTOCOL_DVRCHUNKSTREAMING:
			prot = "HTTPDvrChunch";
			break;
		case IHTTPStreamerSession.SESSIONPROTOCOL_MPEGDASHSTREAMING:
			prot = "HTTPMpegDash";
			break;
		case IHTTPStreamerSession.SESSIONPROTOCOL_SANJOSESTREAMING:
			prot = "HTTPSanjose";
			break;
		case IHTTPStreamerSession.SESSIONPROTOCOL_SMOOTHSTREAMING:
			prot = "HTTPSmooth";
			break;
		case IHTTPStreamerSession.SESSIONPROTOCOL_WEBMSTREAMING:
			prot = "HTTPWebMs";
			break;
		}
		
		return prot;
	}


}
