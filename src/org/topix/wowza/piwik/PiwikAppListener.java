package org.topix.wowza.piwik;

import com.wowza.wms.application.IApplicationNotify;
import com.wowza.wms.application.IApplication;
import com.wowza.wms.logging.WMSLogger;


public class PiwikAppListener implements IApplicationNotify {
	private static WMSLogger logger;
	public static boolean debug = false;
	public String vhostName;
	
	public static String piwikUrl;
	public static String piwikKey;
	public int piwikWebsiteId;

    public PiwikAppListener() {
    	logger = WMSLogger.getLogger("PiwikModule");
    }

	@Override
	public void onApplicationCreate(IApplication application) {
		if (debug)
			logger.info("piwiklistener: entering onApplicationCreate for " + application.getName() + " (website id = " + piwikWebsiteId + ")");
        
		// adding properties for connection to piwik
        synchronized(application.getProperties()) {
            application.getProperties().put(PiwikListener.PROP_PREFIX + "Url", piwikUrl);
            application.getProperties().put(PiwikListener.PROP_PREFIX + "Key", piwikKey);
            application.getProperties().put(PiwikListener.PROP_PREFIX + "WebsiteId", piwikWebsiteId);
            application.getProperties().put(PiwikListener.PROP_PREFIX + "Debug", debug);
        }

		
		// adding module for piwik notifications
		StringBuffer buffer = new StringBuffer(application.readAppConfig(application.getConfigPath()));
        if(buffer.indexOf("<Name>" + PIWIK_MODULE_NAME + "</Name>") == -1 && -1 != buffer.indexOf("</Modules>")) {
            buffer.insert(buffer.indexOf("</Modules>"), "\n\t\t\t<Module><Name>" + PIWIK_MODULE_NAME + "</Name><Description>TopIX Piwik Stats Logging</Description><Class>" + PIWIK_MODULE_CLASS + "</Class></Module>\n\t\t");
            application.writeAppConfig(application.getConfigPath(), buffer.toString());
        }

	}

	@Override
	public void onApplicationDestroy(IApplication application) {
		if (debug)
			logger.info("piwiklistener: entering onApplicationDestroy for " + application.getName());
		// removing properties for connection to piwik
        synchronized(application.getProperties()) {
            application.getProperties().remove(PiwikListener.PROP_PREFIX + "Url");
            application.getProperties().remove(PiwikListener.PROP_PREFIX + "Key");
            application.getProperties().remove(PiwikListener.PROP_PREFIX + "WebsiteId");
            application.getProperties().remove(PiwikListener.PROP_PREFIX + "Debug");
        }


	}

	
	private static String PIWIK_MODULE_NAME = "PiwikModule";
	private static String PIWIK_MODULE_CLASS = "org.topix.wowza.piwik.module.PiwikModule";
	
}
