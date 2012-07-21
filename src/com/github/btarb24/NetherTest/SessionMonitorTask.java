package com.github.btarb24.NetherTest;

import java.sql.*;
import java.util.List;
import java.util.TimerTask;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.github.btarb24.NetherTest.Configuration.Keys;

public class SessionMonitorTask extends TimerTask
{
	
	private DbAccess _dbAccess;
	private Configuration _config;

	public SessionMonitorTask(Logger logger, Configuration config)
	{
		//make a new instance of dbaccess so that we get a new connection. Otherwise we're not thread safe
		_config = config;
		_dbAccess =  new DbAccess(logger, config);
	}
	
	@Override
	public void run() 
	{
		//get the nether world
		World world = Bukkit.getWorld(_config.getProperty(Keys.NETHER_SERVER_NAME));
		
		//sanity  (world may not be loaded yet if plugin just started)
		if (world == null)
			return;
		
		//get a list of all the players in the nether world
		List<Player> players = world.getPlayers();
		
		//dont waste time connecting to db if we dont have players
		if (players.size() == 0)
			return;

		
		for (Player player : players)
		{
			try {
				if (_dbAccess.surpassedLimit(player))
				{
					//persist new db values
					_dbAccess.endNetherSession(player);
					
					//punt them to main world
					player.teleport(Bukkit.getWorld("world").getSpawnLocation());
					
					//tell them why
					player.sendMessage(String.format("You've used all your Nether minutes. You may re-enter in %s hours", _config.getProperty(Keys.ENTRANCE_FREQUENCY)));					
				}
			}
			catch (SQLException e) 
			{ 
				_dbAccess.initDbConnection(); //re-init the connection in case there is a problem
			}
		}
	}
}
