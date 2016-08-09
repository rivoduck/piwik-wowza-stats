package org.topix.wowza.piwik;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.wowza.wms.amf.AMFDataList;
import com.wowza.wms.application.WMSProperties;
import com.wowza.wms.client.IClient;
import com.wowza.wms.request.RequestFunction;
import com.wowza.wms.vhost.IVHost;
import com.wowza.wms.vhost.IVHostNotify;
import com.wowza.wms.logging.WMSLogger;

import org.topix.wowza.piwik.PiwikAppListener;

public class PiwikVhostListener implements IVHostNotify {
	private static WMSLogger logger;
	private static Map<String, PiwikAppListener> listeners = new HashMap<String, PiwikAppListener>();
	public static boolean debug = false;

	
    public PiwikVhostListener() {
        logger = WMSLogger.getLogger("PiwikModule");
        debug = PiwikListener.debug;

        if (debug) {
    		logger.info("piwiklistener: got debug " + debug);
    		logger.info("piwiklistener: got piwikUrl " + PiwikListener.piwikUrl);
    		logger.info("piwiklistener: got piwikKey " + PiwikListener.piwikKey);
    		logger.info("piwiklistener: got piwikDefaultWebsiteId " + PiwikListener.piwikDefaultWebsiteId);
        }
    }


	@Override
	public void onVHostClientConnect(IVHost vhost, IClient inClient,
			RequestFunction function, AMFDataList params) {
	}

	@Override
	public void onVHostCreate(IVHost vhost) {
		
		PiwikAppListener listener = new PiwikAppListener();
		PiwikAppListener.debug=debug;
		listener.vhostName=vhost.getName();
		PiwikAppListener.piwikUrl=PiwikListener.piwikUrl;
		PiwikAppListener.piwikKey=PiwikListener.piwikKey;
		
		vhost.addApplicationListener(listener);
		listeners.put(vhost.getName(), listener);

		if (debug) {
			logger.info("piwiklistener: onVHostCreate, added app listener for " + vhost.getName());
		}

	}

	@Override
	public void onVHostInit(IVHost vhost) {
		WMSProperties vhostProps = vhost.getProperties();
		int piwikWebsiteId = vhostProps.getPropertyInt(PiwikListener.PROP_PREFIX + "WebsiteId", PiwikListener.piwikDefaultWebsiteId);
		listeners.get(vhost.getName()).piwikWebsiteId=piwikWebsiteId;
		if (debug) {
			logger.info("piwiklistener: website id for vhost " + vhost.getName() + " is " + piwikWebsiteId);
		}

	}
	
	@Override
	public void onVHostShutdownComplete(IVHost vhost) {
		vhost.removeApplicationListener(listeners.get(vhost.getName()));

		if (debug)
			logger.info("piwiklistener: onVHostShutdownComplete, removed app listener for " + vhost.getName());
		

	}

	@Override
	public void onVHostShutdownStart(IVHost vhost) {
		// TODO Auto-generated method stub

	}

}
