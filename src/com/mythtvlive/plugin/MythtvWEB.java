package com.mythtvlive.plugin;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.pms.PMS;
import net.pms.encoders.Player;
import net.pms.formats.WEB;

public class MythtvWEB extends WEB {
	private static final String[] PROTOCOLS = {
		"mythtv"
	};
	
	@Override
	public ArrayList<Class<? extends Player>> getProfiles() {
		ArrayList<Class<? extends Player>> profiles = super.getProfiles();
		if(type == VIDEO)
		{
			for (String engine : PMS.getConfiguration().getEnginesAsList(PMS.get().getRegistry())) {
				if (MythtvVideo.ID.equals(engine)) {
					profiles.add(0, MythtvVideo.class);
                }
			}
		}
		return profiles;
	}

	@Override
	public boolean match(String arg0) {
		return arg0 != null && arg0.startsWith("mythtv://");
	}

	@Override
	public String[] getId() {
		return PROTOCOLS;
	}

}
