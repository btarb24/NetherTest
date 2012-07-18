package com.github.btarb24.NetherTest;

import java.sql.*;
import java.util.List;
import java.util.TimerTask;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class SessionMonitorTask extends TimerTask
{
	
	private DbAccess _dbAccess;

	public SessionMonitorTask(Logger logger)
	{
		//make a new instance of dbaccess so that we get a new connection. Otherwise we're not thread safe
		_dbAccess =  new DbAccess(logger);
	}
	
	@Override
	public void run() 
	{
		//get the nether world
		World world = Bukkit.getWorld(Configuration.NETHER_SERVER_NAME);
		
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
					_dbAccess.EndNetherSession(player);
					
					//punt them to main world
					player.teleport(Bukkit.getWorld("world").getSpawnLocation());
					
					//tell them why
					player.sendMessage(String.format("You've used all your Nether minutes. You may re-enter in %d hours", Configuration.ENTRANCE_FREQUENCY));					
				}
			}
			catch (SQLException e) 
			{ 
				_dbAccess.initDbConnection(); //re-init the connection in case there is a problem
			}
		}
	}
}
