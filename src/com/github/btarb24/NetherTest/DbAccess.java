package com.github.btarb24.NetherTest;

import java.util.Calendar;
import java.util.Date;
import java.sql.*;

import org.bukkit.entity.Player;

public class DbAccess 
{
	private Connection _connection = null;
	private Statement _statement = null;
	
	private final String DB_MINS = "minutesUsed";
	private final String DB_TIME = "lastActivity";
	private final String DB_URL = "jdbc:mysql://127.0.0.1:3306/nether?user=root&password=imdeity";
	
	public DbAccess()
	{
		try {
			Class.forName("com.mysql.jdbc.Driver");	
			_connection = DriverManager.getConnection (DB_URL);
			_statement = _connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE);
		}
		catch (ClassNotFoundException e) { e.printStackTrace(); }
		catch (SQLException e) { e.printStackTrace(); }
	}
	
	/* checks the db to see if they have proper permissions and if they
	 * have time remaining to enter the nether.  Throws db exceptions
	 * to allow for sensible retries.
	 */
	public boolean canEnter(Player player) throws SQLException
	{
		//TODO: check and/or disable use of /chest?
		
		//CHECK THE PERMISSIONS
		
		//check that they haven't been in nether too recently
		String query = String.format("SELECT * FROM activity WHERE name = '%s'", player.getName());
		ResultSet rs = _statement.executeQuery(query);	
		
		rs.next(); //advance to the first record
		
		//get the last activity time
		Calendar cal = Calendar.getInstance();
		rs.getTimestamp(DB_TIME, cal);
		
		//add the max frequency to the last activity time
		cal.add(Calendar.HOUR, NetherTest.ENTRANCE_FREQUENCY);
		Calendar currentTime = Calendar.getInstance(); 
		
		//amount of minutes they've used (only loaded if they're still within the a session group)
		int minutes = 0;
		
		if (cal.before(currentTime))
		{//the user gets to start a completely new set of sessions
			
			//reset minutes used to 0 since they're just starting a new session group
			rs.updateInt(DB_MINS, 0);
			//update lastActivity to the current time
			updateTimestamp(rs);
			
			//push the changes to the db
			rs.updateRow();			
		}
		else
		{
			//the user is still in a prior session
			
			//get the minutes used within this session.
			minutes = rs.getInt(DB_MINS);
			
			//they've used too many minutes.. throw up an error and fail out
			if (minutes > NetherTest.MAX_SESSION_LENGTH)
			{
				//calc how long until they can try again
				int calcMin =  getMinuteDiff(cal, currentTime);
				int calcHour = getHourDiff(cal, currentTime);
				
				//output the informational error message
				player.sendMessage(String.format("You've exceeded the %s minute maximum per %s hours", NetherTest.MAX_SESSION_LENGTH, NetherTest.ENTRANCE_FREQUENCY));
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
		player.sendMessage(String.format("Welcome to the Nether World. You have %d minutes left.", NetherTest.MAX_SESSION_LENGTH - minutes));
		
		//cleanup //don't let it throw if we only have the exception on cleanup!
		if (rs != null)
		{
			try { rs.close(); } catch (SQLException e) { }
		}
		
		return true;
	}
	
	private int getHourDiff(Calendar future, Calendar now)
	{
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
		//60,000 milliseconds in a minute
		long min = 60000;
		
		//get the minutes of each
		long mins1 = future.getTimeInMillis() / min;
		long mins2 = now.getTimeInMillis() / min;
		
		//figure out the difference
		int dif = (int) (mins1 - mins2);
		
		//now mod it so that it isn't greater than an hour
		int remainder = dif % 60;
		
		return remainder;
	}
	
	private void updateTimestamp(ResultSet rs) throws SQLException
	{
		//get the current timestamp
		Calendar cal = Calendar.getInstance();
		Date date = cal.getTime();
		long t = date.getTime();
		Timestamp ts = new Timestamp(t);
		
		//update it in the result set (doesn't push to db until you call update row)
		rs.updateTimestamp(DB_TIME, ts);
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
