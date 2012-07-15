package com.github.btarb24.NetherTest;

import java.sql.SQLException;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/* Transitions a player into the nether via player request.
 * Requests are only permissible once per XX hours. Players
 * are unable to bring in foreign items; though, they may 
 * return with whatever items they gather while inside the
 * nether world.
 */
public class NetherTest extends JavaPlugin 
{ 
	//how often a player can enter the nether. Measured in HOURS
	public static final int ENTRANCE_FREQUENCY = 12;
	
	//the max length of time a player may remain in the nether per session. measured in MINUTES
	public static final int MAX_SESSION_LENGTH = 60;
	
	private DbAccess _dbAccess = null;
	
	public void onLoad()
	{ 
		_dbAccess = new DbAccess(getLogger());
		getLogger().info("NetherTest Loaded");
	}
	
	public void onEnable()
	{ 
		getLogger().info("NetherTest Enabled");
	}
	 
	public void onDisable()
	{ 
		getLogger().info("NetherTest Disabled");
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args)
	{	
		//ignore commands that do not begin with nether
		if (!cmd.getName().equalsIgnoreCase("nether"))
			return false;

		if (args.length == 0)
		{
			//TODO: return usage
			return false;
		}

		//the commands that have been coded are not valid for console access.. thus deny it
		Player player = null;
		if (sender instanceof Player)
			player = (Player) sender;
		else
			return true;
		
		//EVALUTE COMMANDS
		String command = args[0].toLowerCase();
		
		if(command.equals("enter"))
		{ //enter the nether world if permission is granted.

			try
			{
				if (! _dbAccess.canEnter(player))
					return true; //access denied. message to player already sent
			}
			catch (SQLException e)
			{ //exception occurred. consider it a failed attempt.  try once more before giving up
				try
				{
					if (! _dbAccess.canEnter(player))
						return true; //access denied. message to player already sent
				}
				catch (SQLException ex)
				{ //exception occurred again. Just display an error and give up
					player.sendMessage("An error occurred.  We cannot send you to the nether right now. Please wait and try again later.");
					
					getLogger().info(ex.getMessage());
					return true;
				}
			}
			
			//if we made it here then we can send them to the nether.
			player.teleport(Bukkit.getWorld("world_nether").getSpawnLocation());
			return true;
		}
		else if(command.equals("exit"))
		{
			if (player.getWorld().getName().equals("world_nether"))
			{
				player.teleport(Bukkit.getWorld("world").getSpawnLocation());
				_dbAccess.exitNether(player);
			}
			else
				player.sendMessage("You must be in the nether world to be able to exit it O.o");
		}
		
		//default fall through
		return false; 
	}
}
