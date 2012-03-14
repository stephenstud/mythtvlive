package com.mythtvlive.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.pms.PMS;
import net.pms.io.BufferedOutputFileImpl;
import net.pms.io.OutputParams;
import net.pms.io.ProcessWrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MythtvProcessWrapper extends Thread implements ProcessWrapper{
	private static final Logger logger = LoggerFactory.getLogger(MythtvProcessWrapper.class);
	private boolean stopping;
	private OutputParams params;
	private BufferedOutputFileImpl bof ;
	private String mythtv_server;
	private int mythtv_port;
	private String mythtv_channel;
	public MythtvProcessWrapper(OutputParams params,String mythtvUrl)
	{
		super("mythtv_process");
		this.stopping = false;
		this.params = params;
		Pattern p = Pattern.compile("^mythtv://([^:]*):([1-9][0-9]*)/(.*)$");
		Matcher m = p.matcher(mythtvUrl);
		if(m.find())
		{
			this.mythtv_server = m.group(1);
			this.mythtv_port = Integer.valueOf(m.group(2));
			this.mythtv_channel = m.group(3);
		}
		else
		{
			logger.error("mythtv url is wrong!!!!!!!!!!!");
			this.mythtv_server = "";
			this.mythtv_port = 0;
			this.mythtv_channel = "";
		}
		
		
	}
	private String[] myth_send_command(InputStream is,OutputStream os,String command) throws IOException
	{
		os.write(String.format("%-8d%s",command.length(),command).getBytes());
		byte[] result_len = new byte[8];
		int count = is.read(result_len,0,8);
		if(count != 8)
			return null;
		int data_len = Integer.parseInt(new String(result_len).trim());
		
		byte[] result_data = new byte[data_len];
		count = is.read(result_data,0,data_len);
		if(count != data_len)
			return null;
		
		return new String(result_data).split("\\[\\]:\\[\\]");
	}
	private int myth_getdata(InputStream  command_is,OutputStream command_os, String recorder_id,String recorder_ip,int recorder_port) throws IOException, InterruptedException
	{
		String[] response;
		byte[] buffer = new byte[30 * 1024];
		response = myth_send_command(command_is,command_os,"QUERY_RECORDER " + recorder_id + "[]:[]GET_CURRENT_RECORDING");
		if(response.length != 41)
		{
			logger.error("QUERY_RECORDER error.");
			return 1;
		}
		String recordingfile = response[8];
		recordingfile = recordingfile.substring(recordingfile.lastIndexOf('/'));
		logger.debug("got recording file [" + recordingfile + "]");
		
		//connect to data
		InetAddress data_address = InetAddress.getByName(recorder_ip);
		InetSocketAddress data_sa = new InetSocketAddress(data_address, recorder_port);
		
		Socket data_socket = new Socket(Proxy.NO_PROXY);
		data_socket.connect(data_sa);
		OutputStream data_os = data_socket.getOutputStream();
		InputStream  data_is = data_socket.getInputStream();
		
		response = myth_send_command(data_is,data_os,"MYTH_PROTO_VERSION 63 3875641D");
		if(response.length != 2 || !"ACCEPT".equals(response[0]))
		{
			logger.error("[data]MYTH_PROTO_VERSION 63 3875641D response error.");
			return 1;
		}
		response = myth_send_command(data_is,data_os,"ANN FileTransfer " + PMS.getConfiguration().getServerHostname() + " 0[]:[]" + recordingfile  + "[]:[]Default");
		if(response.length != 4 || !"OK".equals(response[0]))
		{
			logger.error("[data]ANN FileTransfer response error.");
			return 1;
		}
		String socket_id = response[1];
		int file_size = Integer.parseInt(response[3]);
		
		while(!this.stopping)
		{
			response = myth_send_command(command_is,command_os,"QUERY_FILETRANSFER " + socket_id + "[]:[]REQUEST_BLOCK[]:[]" + buffer.length);
			if(response.length != 1)
			{
				logger.error("QUERY_FILETRANSFER REQUEST_BLOCK response error.");
				return 1;
			}
			file_size = Integer.parseInt(response[0]);
			if(file_size == -1)
			{
				data_os.close();
				data_is.close();
				data_socket.close();
				return -1;
			}
			else if(file_size == 0)
			{
				data_os.close();
				data_is.close();
				data_socket.close();
				return -1;
			}
			else
			{
				int count = data_is.read(buffer, 0, file_size);
				bof.write(buffer, 0, count);
			}
		}
		return 0;
	}
	private int myth_live()
	{
		String[] response;
		try {
			InetAddress command_address = InetAddress.getByName(this.mythtv_server);
			InetSocketAddress command_sa = new InetSocketAddress(command_address, this.mythtv_port);
			
			Socket command_socket = new Socket(Proxy.NO_PROXY);
			command_socket.connect(command_sa);
			OutputStream command_os = command_socket.getOutputStream();
			InputStream  command_is = command_socket.getInputStream();
			
			response = myth_send_command(command_is,command_os,"MYTH_PROTO_VERSION 63 3875641D");
			if(response.length != 2 || !"ACCEPT".equals(response[0]))
			{
				logger.error("MYTH_PROTO_VERSION 63 3875641D response error.");
				return 1;
			}
			response = myth_send_command(command_is,command_os,"ANN Playback " + PMS.getConfiguration().getServerHostname() + " 0");
			if(response.length != 1 || !"OK".equals(response[0]))
			{
				logger.error("ANN Playback response error.");
				return 1;
			}
			response = myth_send_command(command_is,command_os,"GET_NEXT_FREE_RECORDER[]:[]-1");
			if(response.length < 3 || "-1".equals(response[0]))
			{
				logger.error("GET_NEXT_FREE_RECORDER[]:[]-1 error.");
				return 1;
			}
			String recorder_id = response[0];
			String recorder_ip = response[1];
			int		recorder_port = Integer.parseInt(response[2]);
			logger.debug("get recorder: recorder_id [" + recorder_id + "] ip [" + recorder_ip + "]port [" + recorder_port + "]");
			
			response = myth_send_command(command_is,command_os,"QUERY_RECORDER " + recorder_id + "[]:[]SPAWN_LIVETV[]:[]live-seans-laptop-" + System.currentTimeMillis() + "[]:[]0[]:[]" + this.mythtv_channel);
			if(response.length != 1 || !"ok".equals(response[0]))
			{
				logger.error("SPAWN_LIVETV error.");
				return 1;
			}
			
			int timer = 3;
			while(timer > 0)
			{
				response = myth_send_command(command_is,command_os,"QUERY_RECORDER " + recorder_id + "[]:[]IS_RECORDING");
				if(response.length != 1 || !"1".equals(response[0]))
				{
					timer--;
					Thread.sleep(1000);
					continue;
				}
				break;
			}
			while(!this.stopping)
			{
				switch(myth_getdata(command_is,command_os, recorder_id,recorder_ip,recorder_port))
				{
					case 0:
						break;
					case -1:
						continue;
					default:
						return 1;
				}
			}
			
			logger.debug("stopping live tv..............");
			response = myth_send_command(command_is,command_os,"QUERY_RECORDER " + recorder_id + "[]:[]STOP_LIVETV");
			if(response.length != 1 || !"ok".equals(response[0]))
			{
				logger.error("SPAWN_LIVETV error.");
				return 1;
			}
			
			command_is.close();
			command_os.close();
			command_socket.close();
			return 0;
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return -1;
	}
	@Override
	public void run() {
		try
		{
			bof = new BufferedOutputFileImpl(params);
			bof.attachThread(this);
			//this.params.input_pipes[0] = new PipeProcess("mytest" + System.currentTimeMillis());
			if(myth_live() == 0)
			{
				
			}
		
			if (bof != null) {
                bof.close();
            }
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		super.run();
	}
	@Override
	public void runInNewThread() {
		this.start();
	}
	@Override
	public synchronized InputStream getInputStream(long seek) throws IOException {
		if (bof != null) {
            return bof.getInputStream(seek);
        } else {
            return null;
        }
	}
	@Override
	public List<String> getResults() {
		return null;
	}
	@Override
	public void stopProcess() {
		this.stopping = true;
		try {
			this.join(10000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	@Override
	public boolean isDestroyed() {
		return this.stopping;
	}
	private boolean nullable;
	@Override
	public boolean isReadyToStop() {
		return nullable;
	}

	@Override
	public void setReadyToStop(boolean nullable) {
        this.nullable = nullable;
        if(nullable == true)
        	this.stopping = true;
	}

	

	

	

	

	

}
