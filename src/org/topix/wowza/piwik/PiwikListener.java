package org.topix.wowza.piwik;

import com.wowza.wms.application.WMSProperties;
import com.wowza.wms.server.IServer;
import com.wowza.wms.server.IServerNotify2;
import com.wowza.wms.vhost.VHostSingleton;

import org.topix.wowza.piwik.PiwikVhostListener;

// Configured by adding class entries definitions to Server.xml.
// Startup order is: [constructor], onServerConfigLoaded, onServerCreate, onServerInit
// Shutdown order is: onServerShutdownStart, onServerShutdownComplete, [exit]
public class PiwikListener implements IServerNotify2 {
	public static final String PROP_PREFIX = "piwikListener";
	public static boolean debug;
	public static String piwikUrl;
	public static String piwikKey;
	public static int piwikDefaultWebsiteId;

	@Override
	public void onServerCreate(IServer server) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onServerInit(IServer server) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onServerShutdownComplete(IServer server) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onServerShutdownStart(IServer server) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onServerConfigLoaded(IServer server) {
		WMSProperties serverProps = server.getProperties();
		
		debug = serverProps.getPropertyBoolean(PROP_PREFIX + "Debug", false);
		piwikUrl = serverProps.getPropertyStr(PROP_PREFIX + "Url");
		piwikKey = serverProps.getPropertyStr(PROP_PREFIX + "Key");
		piwikDefaultWebsiteId = serverProps.getPropertyInt(PROP_PREFIX + "DefaultWebsiteId", 0);
		VHostSingleton.addVHostListener(new PiwikVhostListener());

	}

}
