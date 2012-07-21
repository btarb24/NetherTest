package com.github.btarb24.NetherTest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

//This is a config that will read the values from a config file. if the file is missing or a key is missing then
//it will create them using the default values in the enum.  Once read, the values are stored in a hash table so
//that the file system isn't continually (and inefficiently) accessed.
public class Configuration {
	
	//an enum of the config keys with string value and default value
	public enum Keys
	{
		//how many days the nether world should last until it gets automatically reset
		DAYS_UNTIL_NETHER_RESET { public String toString() { return "DAYS_UNTIL_NETHER_RESET"; } },
		//this is the last time the server was reset. it is measured in days from epoch. set to something like 1 to have it reset immediately
		LAST_RESET_DAY { public String toString() { return "LAST_RESET_DAY_FROM_EPOCH"; } },
		//how often a player can enter the nether. Measured in HOURS
		ENTRANCE_FREQUENCY { public String toString() { return "ENTRANCE_FREQUENCY_HOURS"; } },
		//the max length of time a player may remain in the nether per session. measured in MINUTES
		MAX_SESSION_LENGTH { public String toString() { return "MAX_SESSION_LENGTH_MINUTES"; } },
		//How often to check if a player is out of nether minutes. Measured in MILLISECONDS
		MONITOR_INTERVAL { public String toString() { return "MONITOR_INTERVAL_MILLISECONDS"; } },
		//name of nether world.  had to pick a new world name since you cannot unload a world with a default name: https://bukkit.atlassian.net/browse/BUKKIT-731
		NETHER_SERVER_NAME { public String toString() { return "NETHER_SERVER_NAME"; } },
		//what percent to have pigzombie drop a nugget. use whole ints. ie:  10 = 10%
		PIGZOMBIE_GOLD_DROP_PERCENT { public String toString() { return "PIGZOMBIE_GOLD_DROP_PERCENT_INTEGER"; } },
		//The name of the minute used field in the db
		DB_FIELD_MINS { public String toString() { return "DB_FIELD_MINS"; } },
		//The name of the last activity field in the db
		DB_FIELD_TIME { public String toString() { return "DB_FIELD_TIME"; } },
		//The name of the playername field in the db
		DB_FIELD_NAME { public String toString() { return "DB_FIELD_NAME"; } },
		//the connection string with credentials in it
		DB_URL { public String toString() { return "DB_URL_WITH_CREDS"; } };

		//store the default values in a convenient place
		public static String getDefaultValue(Keys key) {
			switch(key)
			{
				case DAYS_UNTIL_NETHER_RESET:
					return "7";
				case LAST_RESET_DAY:
					return "1";
				case ENTRANCE_FREQUENCY:
					return "12";
				case MAX_SESSION_LENGTH:
					return "60";
				case MONITOR_INTERVAL:
					return "180000";
				case NETHER_SERVER_NAME:
					return "world_nether_different_name";
				case PIGZOMBIE_GOLD_DROP_PERCENT:
					return "10";
				case DB_FIELD_MINS:
					return "minutesUsed";
				case DB_FIELD_TIME:
					return "lastActivity";
				case DB_FIELD_NAME:
					return "name";
				case DB_URL:
					return "jdbc:mysql://127.0.0.1:3306/nether?user=root&password=imdeity";
				default:
					return "";
			}
		}
	}
	
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
		
		//this is completely unncessary but it causes the config file to be completely written with defaults
		//when the application is launched in case something was missing from it.
		getProperty(Keys.DAYS_UNTIL_NETHER_RESET);
		getProperty(Keys.LAST_RESET_DAY);
		getProperty(Keys.ENTRANCE_FREQUENCY);
		getProperty(Keys.MAX_SESSION_LENGTH);
		getProperty(Keys.MONITOR_INTERVAL);
		getProperty(Keys.NETHER_SERVER_NAME);
		getProperty(Keys.PIGZOMBIE_GOLD_DROP_PERCENT);
		getProperty(Keys.DB_URL);
		getProperty(Keys.DB_FIELD_MINS);
		getProperty(Keys.DB_FIELD_NAME);
		getProperty(Keys.DB_FIELD_TIME);
	}
 
	public String getProperty(Keys key)
	{
		String value = this.configFile.getProperty(key.toString());
		if(value == null)
		{
			String defaultValue = Keys.getDefaultValue(key);
			//well the property wasn't in the file.  Let's add it and use the requested default value
			setProperty(key, defaultValue);
			return defaultValue;
		}
		else
			return value;
	}
	
	public long getPropertyLong(Keys key)
	{
		return Long.parseLong(getProperty(key));
	}
	
	public int getPropertyInt(Keys key)
	{
		return Integer.parseInt(getProperty(key));
	}
	
	public void setProperty(Keys key, String value)
	{
		this.configFile.setProperty(key.toString(), value);//update hash
		try {
			this.configFile.store(new FileOutputStream(CONFIG_FILE_LOC), null); //persist to disk
		} catch (FileNotFoundException e) { //ignored
		} catch (IOException e) {		}   //ignored
	}
	
	private void ensureConfigFileExists() throws IOException
	{
		File file = new File(CONFIG_FILE_LOC);
		//check if file is there
		if (!file.exists())
			file.createNewFile();
	}
}
