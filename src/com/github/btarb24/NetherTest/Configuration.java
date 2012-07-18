package com.github.btarb24.NetherTest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

//Easier to find configurable consts if i put them all in one spot. 
public class Configuration {

	//how many days the nether world should last until it gets automatically reset
	public static final int DAYS_UNTIL_NETHER_RESET = 7;
	
	//how often a player can enter the nether. Measured in HOURS
	public static final int ENTRANCE_FREQUENCY = 12;
	
	//the max length of time a player may remain in the nether per session. measured in MINUTES
	public static final int MAX_SESSION_LENGTH = 60;
	
	//3 minutes due to potentially high db access rate
	public static final int MONITOR_INTERVAL = 3*60*1000; 
	
	//name of nether world.  used it in too many places and feared typo
	//had to pick a new world name since you cannot unload a world with a default name: https://bukkit.atlassian.net/browse/BUKKIT-731
	public static final String NETHER_SERVER_NAME = "world_nether_different_name";
	
	//what percent to have pigzombie drop a nugget
	public static final int PIGZOMBIE_GOLD_DROP_PERCENT = 10; 
	

	public static final String DB_MINS = "minutesUsed";
	public static final String DB_TIME = "lastActivity";
	public static final String DB_NAME = "name";
	public static final String DB_URL = "jdbc:mysql://127.0.0.1:3306/nether?user=root&password=imdeity";

	
	private static final String CONFIG_FILE_LOC = ".\\NetherTest.properties";
	
	//here's a bit of code to read from a config file isntead of using constants.
	//This class really should be refactored to store the above consts in the cfg file as well
	Properties configFile;
	public Configuration() throws Exception
	{
		//in case someone forgot to make it.  default values are stored in the accessors
		ensureConfigFileExists();
		
		configFile = new java.util.Properties();
		configFile.load(new FileInputStream(CONFIG_FILE_LOC));
	}
 
	public String getProperty(String key)
	{
		String value = this.configFile.getProperty(key);
		return value;
	}
	
	public String getProperty(String key, String defaultValue)
	{
		String val = getProperty(key);
		if(val == null)
		{
			//well the property wasn't in the file.  Let's add it and use the requested default value
			setProperty(key, defaultValue);
			return defaultValue;
		}
		else
			return val;
	}
	
	public void setProperty(String key, String value)
	{
		this.configFile.setProperty(key, value);//update hash
		try {
			this.configFile.store(new FileOutputStream(CONFIG_FILE_LOC), null); //persist to disk
		} catch (FileNotFoundException e) { //ignored
		} catch (IOException e) {		}   //ignored
	}
	
	public boolean containsKey(String key)
	{
		return this.configFile.containsKey(key);
	}
	
	private void ensureConfigFileExists() throws IOException
	{
		File file = new File(CONFIG_FILE_LOC);
		//check if file is there
		if (!file.exists())
			file.createNewFile();
	}
}
