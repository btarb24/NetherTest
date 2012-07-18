package com.github.btarb24.NetherTest;

//Easier to find configurable consts if i put them all in one spot. 
public class Configuration {

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
}
