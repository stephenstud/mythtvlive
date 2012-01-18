package com.mythtvlive.plugin;

import java.util.ArrayList;

import net.pms.PMS;
import net.pms.encoders.Player;
import net.pms.formats.WEB;

public class MythtvWEB extends WEB {

	private static final String[] PROTOCOLS = {
		"mythtv"
	};
	
	@Override
	public ArrayList<Class<? extends Player>> getProfiles() {
		PMS.minimal("mythtvweb getProfiles");
		ArrayList<Class<? extends Player>> profiles = super.getProfiles();
		PMS.minimal("type=" + type);
		if(type == VIDEO)
		{
			for (String engine : PMS.getConfiguration().getEnginesAsList(PMS.get().getRegistry())) {
				PMS.minimal("engine=" + engine);
				if (MythtvVideo.ID.equals(engine)) {
					PMS.minimal("adding myself");
                    profiles.add(0, MythtvVideo.class);
                }
			}
		}
		return profiles;
	}

	@Override
	public boolean match(String arg0) {
		PMS.minimal("matching......." + arg0);
		return arg0 != null && arg0.startsWith("mythtv://");
	}

	@Override
	public String[] getId() {
		PMS.minimal("getting protocols");
		return PROTOCOLS;
	}

}
