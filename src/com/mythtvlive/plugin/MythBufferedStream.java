package com.mythtvlive.plugin;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import net.pms.PMS;

public class MythBufferedStream extends InputStream {

	public byte[] buffer;
	int pos,len;
	boolean dropoverlack = false;
	private boolean closed;
	
	public void setClosed(boolean closed) {
		this.closed = closed;
	}
	public boolean isClosed() {
		return closed;
	}
	public MythBufferedStream()
	{
		buffer = new byte[1024*1024*10];
		pos = 0; len=0;
		closed = false;
	}
	@Override
	public synchronized int read() throws IOException {
		if(len > 0) {
			pos++;
			int res = buffer[pos-1] >= 0 ? buffer[pos-1] : 255+buffer[pos-1];
			len--;
			if(pos == buffer.length)
			{	
				pos = 0;
			}
			this.notifyAll();
			PMS.debug("read one byte. current pos=" + pos + " value=" + res);
			return res;
		}
		PMS.debug("no data available");
		throw new IOException("no data available");
	}

	@Override
	public synchronized int available() throws IOException {
		return len;
	}

	@Override
	public synchronized void close() throws IOException {
		PMS.debug(".................closed..............");
		this.notifyAll();
		this.closed = true;
		this.clear();
	}
	public void clear()
	{
		this.len = 0;
		this.pos = 0;
	}
	
	@Override
	public synchronized int read(byte[] b, int off, int alen){
		int bytesinend = (pos + len) > buffer.length ? buffer.length - pos - len : len;
		int copyend = Math.min(alen, bytesinend);
		int copybegin = 0;
		System.arraycopy(this.buffer, pos,b , 0, copyend);
		this.len -= copyend;
		this.pos += copyend;
		if(this.pos == this.buffer.length)
		{
			this.pos = 0;
		}
		alen -= copyend;
		if(alen > 0 && len > 0)
		{
			copybegin = Math.min(alen,this.len);
			System.arraycopy(this.buffer, 0, b, copyend, copybegin);
			this.pos = copybegin;
			this.len -= copybegin;
			if(this.len == 0)
			{
				this.pos = 0;
			}
		}
		this.notifyAll();
		PMS.debug("read. current pos = " + pos + " len=" + len);
		return copybegin + copyend;
	}

	public synchronized int write(byte[] b,int alen)
	{
		int originallen = alen;
		
		while(alen > 0)
		{
			int writetoend = Math.min(buffer.length - pos - len > 0 ? buffer.length - pos - len : 0, alen);
			if(writetoend > 0)
			{
				System.arraycopy(b, 0, buffer, pos + len, writetoend);
				alen -= writetoend;
				len += writetoend;
			}
			int writetostart = Math.min(buffer.length - len, alen);
			if(writetostart > 0)
			{
				System.arraycopy(b, writetoend, buffer, 0, writetoend);
				alen -= writetostart;
				len += writetostart;
			}
			
			try {
				if(alen > 0 && !dropoverlack)
					this.wait();
				else
					break;
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
		}
		PMS.debug("write:current pos = " + pos + " len=" + len);
		
		return originallen - alen;
		
	}

}
