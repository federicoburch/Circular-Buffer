package com.mobvcasting.circularbuffer;


import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import net.bican.wordpress.MediaObject;
import net.bican.wordpress.Page;
import net.bican.wordpress.Wordpress;
import redstone.xmlrpc.XmlRpcClient;
import redstone.xmlrpc.XmlRpcStruct;
import android.os.AsyncTask;
import android.util.Log;

import ch.boye.httpclientandroidlib.client.HttpClient;

/**
 * Class for fetching the feed content in the background.
 * 
 */
public class XMLRPCPublisher extends AsyncTask<Item, Integer, Item>
{
	public final static String LOGTAG = "XMLRPC PUBLISHER";

	SocialReporter socialReporter;

	XMLRPCPublisherCallback itemPublishedCallback;

	public void setXMLRPCPublisherCallback(XMLRPCPublisherCallback _itemPublishedCallback)
	{
		itemPublishedCallback = _itemPublishedCallback;
	}

	public interface XMLRPCPublisherCallback
	{
		public void itemPublished(Item _item);
	}

	public XMLRPCPublisher(SocialReporter _socialReporter)
	{
		super();
		socialReporter = _socialReporter;
	}

	@Override
	protected Item doInBackground(Item... params)
	{
		Item item = new Item();
		if (params.length == 0)
		{
			Log.v(LOGTAG, "doInBackground params length is 0");
		}
		else
		{
			item = params[0];

			try
			{
				XmlRpcClient.setContext(socialReporter.applicationContext);

				if (socialReporter.useTor())
				{
					XmlRpcClient.setProxy(true, "SOCKS", SocialReader.PROXY_HOST, SocialReader.PROXY_HTTP_PORT);
				}
				else
				{
					XmlRpcClient.setProxy(false, null, null, -1);
				}

				Settings settings = new Settings(socialReporter.applicationContext);
				String xmlRPCUsername = settings.getXMLRPCUsername();
				String xmlRPCPassword = settings.getXMLRPCPassword();
				
				if (xmlRPCUsername == null || xmlRPCPassword == null) {
					
					String nickname = settings.nickname();
					if (nickname == null) {
						nickname = "";
					}
					
					// acxu.createUser
					ArrayList arguments = new ArrayList();
					arguments.add(nickname);
					XmlRpcClient xpc = new XmlRpcClient(new URL(SocialReporter.XMLRPC_ENDPOINT));
					String result = (String) xpc.invoke("acxu.createUser", arguments);
					Log.v(LOGTAG,"From wordpress: " + result);
					String[] sresult = result.split(" ");
					if (sresult.length == 2) {
						xmlRPCUsername = sresult[0];
						xmlRPCPassword = sresult[1];
						Log.v(LOGTAG,"username: " + xmlRPCUsername);
						Log.v(LOGTAG,"username: " + xmlRPCPassword);
						
						settings.setXMLRPCUsername(xmlRPCUsername);
						settings.setXMLRPCPassword(xmlRPCPassword);
					}
				}
				
				if (xmlRPCUsername != null && xmlRPCPassword != null) 
				{
					//Log.v(LOGTAG, "Logging into Wordpress: " + SocialReporter.XMLRPC_USERNAME + '@' + SocialReporter.XMLRPC_ENDPOINT);
					//Wordpress wordpress = new Wordpress(SocialReporter.XMLRPC_USERNAME, SocialReporter.XMLRPC_PASSWORD, SocialReporter.XMLRPC_ENDPOINT);
	
					Log.v(LOGTAG, "Logging into Wordpress: " + xmlRPCUsername + '@' + SocialReporter.XMLRPC_ENDPOINT);
					Wordpress wordpress = new Wordpress(xmlRPCUsername, xmlRPCPassword, SocialReporter.XMLRPC_ENDPOINT);
	
					Page page = new Page();
					page.setTitle(item.getTitle());
	
					StringBuffer sbBody = new StringBuffer();
					sbBody.append(item.getDescription());
	
					ArrayList<MediaContent> mediaContent = item.getMediaContent();
					for (MediaContent mc : mediaContent)
					{
						//String filePath = mc.getFilePathFromLocalUri(socialReporter.applicationContext);
						//String filePath = mc.getUrl();
						URI fileUri = new URI(mc.getUrl());
						Log.v(LOGTAG,"filePath: "+fileUri.getPath());
						if (fileUri != null)
						{
							File f = new File(fileUri.getPath());
							MediaObject mObj = wordpress.newMediaObject("image/jpeg", f, false);
	
							if (mObj != null)
							{
		
								sbBody.append("\n\n<a href=\"" + mObj.getUrl() + "\">" + mObj.getUrl() + "</a>");
	
								// I don't think this will work
								/*
								 * XmlRpcArray custom_fields = new XmlRpcArray();
								 * CustomField cf = new CustomField();
								 * cf.setKey("enclosure");
								 * cf.setValue(mObj.getUrl());
								 * custom_fields.add(cf);
								 * page.setCustom_fields(custom_fields);
								 */
	
								// This should
								XmlRpcStruct enclosureStruct = new XmlRpcStruct();
								enclosureStruct.put("url", mObj.getUrl());
								enclosureStruct.put("length", f.length());
								enclosureStruct.put("type", mObj.getType());
								page.setEnclosure(enclosureStruct);
	
							}
						}
					}
	
					page.setDescription(sbBody.toString());
					boolean publish = true;
	
					String postId = wordpress.newPost(page, publish);
					Log.v(LOGTAG, "Posted: " + postId);
				} else {
					Log.e(LOGTAG,"Can't publish, no username/password");
				}

				// return postId;
			}
			catch (MalformedURLException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			/*
			 * catch (XmlRpcFault e) { // TODO Auto-generated catch block
			 * e.printStackTrace(); }
			 */
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		return item;
	}

	@Override
	protected void onPostExecute(Item item)
	{
		if (itemPublishedCallback != null)
		{
			itemPublishedCallback.itemPublished(item);
		}
	}
}
