package com.mythtvlive.plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;

import javax.swing.JComponent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.pms.PMS;
import net.pms.configuration.PmsConfiguration;
import net.pms.encoders.Player;
import net.pms.external.ExternalListener;
import net.pms.formats.Format;

public class MythtvPlugin implements ExternalListener{

	private PMS pms;
	private PmsConfiguration configuration;
	private MythtvVideo mythtvVideo;
	private static final Logger logger = LoggerFactory.getLogger(MythtvPlugin.class);
	public MythtvPlugin(){
		pms = PMS.get();
		configuration = PMS.getConfiguration();
		
		mythtvVideo = new MythtvVideo(configuration,this);
		ArrayList<Format> extensions = pms.getExtensions();
		logger.debug("adding extension");
		extensions.add(new MythtvWEB());
		logger.debug("added. now count=" + extensions.size());
		registerPlayer(mythtvVideo);
		
	}
	
	private void registerPlayer(MythtvVideo mythtvVideo) {
        try {
            Method pmsRegisterPlayer = pms.getClass().getDeclaredMethod("registerPlayer", Player.class);
            pmsRegisterPlayer.setAccessible(true);
            pmsRegisterPlayer.invoke(pms, mythtvVideo);
            logger.debug("player registered.");
        } catch (Exception e) {
        	e.printStackTrace();
        	logger.debug("player register failed.");
        }
    }	
	@Override
	public JComponent config() {
		return null;
	}

	@Override
	public String name() {
		return "MythtvPlugin";
	}

	@Override
	public void shutdown() {

	}

}
