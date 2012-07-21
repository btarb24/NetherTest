package com.github.btarb24.NetherTest;

import java.util.Calendar;
import java.util.Date;
import java.util.logging.Logger;
import java.sql.*;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DbAccess 
{	
	private Connection _connection = null; //the mysql db connection
	private Statement _statement = null;   //the db statement to reuse
	private Logger _logger = null;         //logger from the main class
	private Configuration _config = null;
	
	public DbAccess(Logger logger, Configuration config)
	{
		_logger = logger;
		_config = config;
		
		initDbConnection();
	}

	public void constructTable()
	{
		try {
			//exure table exists in db
			String sql = "CREATE TABLE IF NOT EXISTS`activity` (" + 
							"`name` varchar(45) NOT NULL," +
							"`lastActivity` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
							"`minutesUsed` int(11) NOT NULL DEFAULT '0'," +
							"PRIMARY KEY (`name`)," +
							"UNIQUE KEY `name_UNIQUE` (`name`)" +
							") ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='log of last activity (enter/exit) and how many minutes were used'";
			_statement.executeUpdate(sql);
		} catch (SQLException e) {
			_logger.severe(String.format("Failed to check if table exists or create it.. likely DB connection issue -- %s", e.getMessage()));
		}
	}

	public void initDbConnection()
	{
		try {
			Class.forName("com.mysql.jdbc.Driver");	
			
			//init the connection if it is null or closed
			if (_connection == null || _connection.isClosed())
			{
				_connection = DriverManager.getConnection (_config.getProperty(Configuration.Keys.DB_URL));
				_statement = null;
			}
			
			//init the statement if it is null or closed
			if (_statement == null || _statement.isClosed())
				_statement = _connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			
			//ensure the activity table exists in the db
			constructTable();
		}
		catch (ClassNotFoundException e) { _logger.severe(e.getMessage()); }
		catch (SQLException e) {  _logger.severe(e.getMessage()); }
	}
	
	/* checks the db to see if they have proper permissions and if they
	 * have time remaining to enter the nether.  Throws db exceptions
	 * to allow for sensible retries.
	 */
	public boolean canEnter(Player player) throws SQLException
	{
		//always allow admins
		if (player.hasPermission("Deity.nether.override"))
			return true;
		
		initDbConnection(); //re-init the connection in case there is a problem
		
		//TODO: check and/or disable use of /chest?
		
		//get the player's record from the db
		ResultSet rs = retrieveRecord(player.getName());
		
		//get the last activity time
		Calendar cal = getTimestampFromDb(rs);
		
		//add the max frequency to the last activity time
		cal.add(Calendar.HOUR, _config.getPropertyInt(Configuration.Keys.ENTRANCE_FREQUENCY));
		Calendar currentTime = Calendar.getInstance(); 
		
		//amount of minutes they've used (only loaded if they're still within the a session group)
		int minutes = 0;
		
		if (cal.before(currentTime))
		{//the user gets to start a completely new set of sessions
			
			//reset minutes used to 0 since they're just starting a new session group
			rs.updateInt(_config.getProperty(Configuration.Keys.DB_FIELD_MINS), 0);
			//update lastActivity to the current time
			updateTimestamp(rs);
			
			//push the changes to the db
			rs.updateRow();			
		}
		else
		{
			//the user is still in a prior session
			
			//get the minutes used within this session.
			minutes = rs.getInt(_config.getProperty(Configuration.Keys.DB_FIELD_MINS));
			
			//they've used too many minutes.. throw up an error and fail out
			if (minutes > _config.getPropertyInt(Configuration.Keys.MAX_SESSION_LENGTH))
			{
				//calc how long until they can try again
				int calcMin =  getMinuteDiff(cal, currentTime);
				int calcHour = getHourDiff(cal, currentTime);
				if (calcMin > 0 && calcMin != 60) //decrement the hour by 1 if we have minutes (not 0 or 60)
					calcHour--;
				if (calcMin == 60) //having a value of 60 minutes is silly
					calcMin = 0;
				
				//output the informational error message
				player.sendMessage(String.format("You've exceeded the %s minute maximum per %s hours", _config.getProperty(Configuration.Keys.MAX_SESSION_LENGTH), _config.getProperty(Configuration.Keys.ENTRANCE_FREQUENCY)));
				player.sendMessage(String.format("Please wait %d hours %d minutes to try again", calcHour, calcMin) );
				return false;
			}
			else
			{ //they still have minutes left.. let them use em up
				//update lastActivity to the current time
				updateTimestamp(rs);
				
				//push the changes to the db
				rs.updateRow();					
			}
		}
		
		//Permission granted! inform them of how many minutes they have left
		player.sendMessage(String.format("Welcome to the Nether World. You have %d minutes left.", _config.getPropertyInt(Configuration.Keys.MAX_SESSION_LENGTH) - minutes));
		
		//cleanup //don't let it throw if we only have the exception on cleanup!
		if (rs != null)
		{
			try { rs.close(); } catch (SQLException e) { }
		}
		
		return true;
	}
	
	public boolean surpassedLimit (Player player) throws SQLException
	{
		//override has no limit
		if (player.hasPermission("Deity.nether.override"))
			return false;
		
		//get their record
		ResultSet rs = retrieveRecord(player.getName());

		//get the last activity time
		Calendar cal = getTimestampFromDb(rs);
		
		//current time
		Calendar currentTime = Calendar.getInstance(); 
		
		//how many minutes were just spent in nether
		int minutesSpent = getAbsoluteMinuteDiff(currentTime, cal);
					
		//how many minutes were previously spent in nether
		int previousMinutes = rs.getInt(_config.getProperty(Configuration.Keys.DB_FIELD_MINS));
		
		//add it and save it to the db
		int totalMinutes = minutesSpent + previousMinutes;
		
		//did they exceed?
		return totalMinutes > _config.getPropertyInt(Configuration.Keys.MAX_SESSION_LENGTH);
	}
	
	public void exitNether(Player player)
	{		
		ResultSet rs = null;
		try {
			rs = retrieveRecord(player.getName());

			//get the last activity time
			Calendar cal = getTimestampFromDb(rs);

			//current time
			Calendar currentTime = Calendar.getInstance(); 
			
			//how many minutes were just spent in nether
			int minutesSpent = getAbsoluteMinuteDiff(currentTime, cal);
						
			//how many minutes were previously spent in nether
			int previousMinutes = rs.getInt(_config.getProperty(Configuration.Keys.DB_FIELD_MINS));
			
			//add it and save it to the db
			int totalMinutes = minutesSpent + previousMinutes;
			rs.updateInt(_config.getProperty(Configuration.Keys.DB_FIELD_MINS), totalMinutes);
			
			//and update the last activity time
			updateTimestamp(rs);
			
			//push the changes to the db
			rs.updateRow();	

			player.sendMessage(String.format("You just spent %d minutes in Nether and a total of %d. The limit is %s.", minutesSpent, totalMinutes, _config.getProperty(Configuration.Keys.MAX_SESSION_LENGTH)));
			
		} catch (SQLException e) {
			_logger.warning("Couldn't access db to exit nether. -- " + e.getMessage());
			return;
		}
		
		//cleanup //don't let it throw if we only have the exception on cleanup!
		if (rs != null)
		{
			try { rs.close(); } catch (SQLException e) { }
		}
	}
	
	public void outputInfo(CommandSender sender, String playername)
	{
		ResultSet rs = null;
		
		try {
			rs = retrieveRecord(playername);

			//get the last activity time
			Calendar cal = getTimestampFromDb(rs);
			Calendar currentTime = Calendar.getInstance(); 

			//if they're currently in the nether then count those minutes since they entered the world.
			int currentNetherMins = 0;
			Player player = Bukkit.getPlayerExact(playername);
			if (player != null && player.getWorld().getName().equals(_config.getProperty(Configuration.Keys.NETHER_SERVER_NAME)))
			{
				currentNetherMins = getMinuteDiff(currentTime, cal);
				if (currentNetherMins < 0)
					currentNetherMins = 0;
			}
			
			//check if their max session time has reset or not so we know which msg to show
			cal.add(Calendar.HOUR, _config.getPropertyInt(Configuration.Keys.ENTRANCE_FREQUENCY));
			if (cal.after(currentTime))
			{	
				//how many minutes were previously spent in nether
				int previousMinutes = rs.getInt(_config.getProperty(Configuration.Keys.DB_FIELD_MINS));
				
				//calc how long until they can try again
				int calcMin =  getMinuteDiff(cal, currentTime);
				int calcHour = getHourDiff(cal, currentTime);	
				if (calcMin > 0 && calcMin != 60) //decrement the hour by 1 if we have minutes (not 0 or 60)
					calcHour--;
				if (calcMin == 60) //having a value of 60 minutes is silly
					calcMin = 0;
				
				sender.sendMessage(String.format(
						"%s used %d of %s minutes within a %s hour period. The minutes will refresh if the nether isn't re-entered for %d hours %d minutes.",
						playername,
						previousMinutes + currentNetherMins,
						_config.getProperty(Configuration.Keys.MAX_SESSION_LENGTH),
						_config.getProperty(Configuration.Keys.ENTRANCE_FREQUENCY),
						calcHour,
						calcMin));
			}
			else //all minute available.
			{
				sender.sendMessage(String.format("%s has all %s minutes available.", playername,  _config.getProperty(Configuration.Keys.MAX_SESSION_LENGTH)));;
			}
		} 
		catch (SQLException e) {
			sender.sendMessage("Sorry, an error occurred while pulling up your details. Please try again later.");
			initDbConnection(); //re-init the connection in case there is a problem
		}
		
		//cleanup //don't let it throw if we only have the exception on cleanup!
		if (rs != null)
		{
			try { rs.close(); } catch (SQLException e) { }
		}
	}

	public void endNetherSession(Player player)
	{//Player needs their session minutes persisted and timestamp updated.

		ResultSet rs = null;
		
		try {
			//get their record
			rs = retrieveRecord(player.getName());

			//set time to now
			updateTimestamp(rs);

			//max minutes
			rs.updateInt(_config.getProperty(Configuration.Keys.DB_FIELD_MINS), _config.getPropertyInt(Configuration.Keys.MAX_SESSION_LENGTH));

			//push the changes to the db
			rs.updateRow();	
		} 
		catch (SQLException e) {
			_logger.warning("Hmm, i couldn't persist an endNetherSession to the db?  -- " + e.getMessage());
			initDbConnection(); //re-init the connection in case there is a problem
		}
		
		//cleanup //don't let it throw if we only have the exception on cleanup!
		if (rs != null)
		{
			try { rs.close(); } catch (SQLException e) { }
		}
	}
	
	public boolean clearSessionInfo(String playerName)
	{
		boolean success = false;
		ResultSet rs = null;
		
		try {
			//get their record
			rs = retrieveRecord(playerName);
			
			rs.deleteRow(); //delete the user record to effectively clear their session info
			success = true; //ok, the update completed
		} 
		catch (SQLException e) {
			_logger.warning("Hmm, i couldn't persist a session clear to the db?  -- " + e.getMessage());
			initDbConnection(); //re-init the connection in case there is a problem
		}
		
		//cleanup //don't let it throw if we only have the exception on cleanup!
		if (rs != null)
		{
			try { rs.close(); } catch (SQLException e) { }
		}
		
		//return the answer so we know if we have to show a message to the caller or not
		return success;
	}
	
	private ResultSet retrieveRecord(String playername) throws SQLException
	{
		//build query and execute it
		String query = String.format("SELECT * FROM activity WHERE name = '%s'", playername);
		ResultSet rs = _statement.executeQuery(query);	
		
		//move to first record if there is one
		if (! rs.next())
		{//they don't have a record yet.. make one so we can return it
			rs.moveToInsertRow();//move to insert row
			rs.updateString(_config.getProperty(Configuration.Keys.DB_FIELD_NAME), playername); //make a record (other fields are auto-pop)
			rs.insertRow(); //persist into result set and into db
			rs.absolute(1); //move it to the first row (now that we have one)
		}
		
		//return the result set with it positioned on the correct (and updatable) row
		return rs;
	}
	
	private int getHourDiff(Calendar future, Calendar now)
	{
		//sanity check
		if (future.before(now))
			return 0;
		
		//3,600,000 milliseconds in an hour
		long hour = 3600000;
		
		//get the minutes of each
		long hours1 = future.getTimeInMillis() / hour;
		long hours2 = now.getTimeInMillis() / hour;
		
		//figure out the difference
		int dif = (int) (hours1 - hours2);
				
		return dif;
	}
	
	private int getMinuteDiff(Calendar future, Calendar now)
	{	
		//get total minutes different
		int mins = getAbsoluteMinuteDiff(future, now);
		
		//now mod it so that it isn't greater than an hour
		int remainder = mins % 60;
		
		return remainder;
	}
	
	private int getAbsoluteMinuteDiff(Calendar future, Calendar now)
	{
		//sanity check
		if (future.before(now))
			return 0;
		
		//60,000 milliseconds in a minute
		long min = 60000;
		
		//get the minutes of each
		long mins1 = future.getTimeInMillis() / min;
		long mins2 = now.getTimeInMillis() / min;
		
		//figure out the difference
		int dif = (int) (mins1 - mins2);
				
		return dif;
	}
	
	private Calendar getTimestampFromDb(ResultSet rs) throws SQLException
	{
		//prep a cal to use
		Calendar cal = Calendar.getInstance();
		
		//get the val from db
		Timestamp dbTime = rs.getTimestamp(_config.getProperty(Configuration.Keys.DB_FIELD_TIME), cal);
		
		//push it into the cal
		cal.setTime(dbTime);
		
		return cal;
	}
	
	private void updateTimestamp(ResultSet rs) throws SQLException
	{
		//get the current timestamp
		Calendar cal = Calendar.getInstance();
		Date date = cal.getTime();
		long t = date.getTime();
		Timestamp ts = new Timestamp(t);
		
		//update it in the result set (doesn't push to db until you call update row)
		rs.updateTimestamp(_config.getProperty(Configuration.Keys.DB_FIELD_TIME), ts);
	}
		
	protected void finalize()
	{
		//close the connection to release it back to the pool
		if (_connection != null)
		{
			try 
			{
				_connection.close();
			}
			catch (SQLException e) 
			{//eat me ..nom nom nom
				e.printStackTrace();
			}
		}
	}
}
