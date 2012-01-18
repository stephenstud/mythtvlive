package com.mythtvlive.plugin;

import java.io.IOException;

import net.pms.PMS;
import net.pms.configuration.PmsConfiguration;
import net.pms.dlna.DLNAMediaInfo;
import net.pms.dlna.DLNAResource;
import net.pms.encoders.MEncoderWebVideo;
import net.pms.io.OutputParams;
import net.pms.io.ProcessWrapper;

public class MythtvVideo extends MEncoderWebVideo {
	static final long MKFIFO_SLEEP = 2000;
	private static final String DEFAULT_MIME_TYPE = "video/mpeg";
	public static final String ID = "MythtvLIVE";
	public static final boolean isWindows = PMS.get().isWindows();
	public MythtvVideo(PmsConfiguration configuration,MythtvPlugin plugin) {
		super(configuration);
	}

	@Override
	public String mimeType() {
		return DEFAULT_MIME_TYPE;
	}
	@Override
    public String executable() {
       return super.executable();
    }
	@Override
	public String id() {
		return ID;
	}

	@Override
	public String name() {
		return ID;
	}
	@Override
	public ProcessWrapper launchTranscode(
	        String oldURI,
	        DLNAResource dlna,
	        DLNAMediaInfo media,
	        OutputParams params
	    ) throws IOException{
		params.minBufferSize = params.minFileSize;
		params.secondread_minsize = 100000;
		params.waitbeforestart = 6000;
		
		MythtvProcessWrapper transcoderProcess = new MythtvProcessWrapper(params,oldURI);
		transcoderProcess.runInNewThread();
        try {
			Thread.sleep(200);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return transcoderProcess;
		
	}
	
	
}
