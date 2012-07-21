package com.github.btarb24.NetherTest.cmds;

import java.util.List;
import java.sql.SQLException;
import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.github.btarb24.NetherTest.*;
import com.github.btarb24.NetherTest.Configuration.Keys;

public class NetherCommand implements CommandExecutor {

	private NetherTest _plugin;
	private Configuration _config;
	private DbAccess _dbAccess;
	
	public NetherCommand(NetherTest plugin, DbAccess access, Configuration config)
	{
		_plugin = plugin;
		_config = config;
		_dbAccess = access;
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) 
	{
		//ignore commands that do not begin with nether
		if (!cmd.getName().equalsIgnoreCase("nether"))
			return true; //wrong command.. howd it get here??

		//ensure they at least have the general perm
		if (! sender.hasPermission("Deity.nether.general"))
		{
			sender.sendMessage("You do not have permission to access the nether command");
			return true;
		}
		
		if (args.length == 0)
		{
			//argument fail. output usage
			outputUsage(sender);
			return true;
		}
			
		//EVALUTE COMMANDS
		String command = args[0].toLowerCase(); //lcase it now so we dont have to lcase at each compare
		
		if(command.equals("enter") || command.equals("join"))
		{ //enter the nether world if permission is granted.
			
			//not a console command. only allow players
			if (denyConsole(sender))
				return true;
			
			Player player = (Player)sender;
			
			//make sure they're not already in the nether. ignore them if they're dumb
			if (player.getWorld().getName().equals(_config.getProperty(Configuration.Keys.NETHER_SERVER_NAME)))
			{
				sender.sendMessage("You're already in the Nether.");
				return true;
			}	
			
			try
			{//make sure they have time left and they have a valid inventory
				if (!_plugin.isInventoryValid(player) || !_dbAccess.canEnter(player))
					return true; //access denied. message to player already sent
			}
			catch (SQLException e)
			{ //exception occurred. consider it a failed attempt.  try once more before giving up
				_dbAccess.initDbConnection(); //re-init the connection in case there is a problem
				try
				{
					if (! _dbAccess.canEnter(player))
						return true; //access denied. message to player already sent
				}
				catch (SQLException ex)
				{ //exception occurred again. Just display an error and give up
					player.sendMessage("An error occurred.  We cannot send you to the nether right now. Please wait and try again later.");
					
					_plugin.getLogger().info(ex.getMessage());
					return true;
				}
			}
			
			//if we made it here then we can send them to the nether.
			player.teleport(_plugin.getNetherSpawnLoc(Bukkit.getWorld(_config.getProperty(Configuration.Keys.NETHER_SERVER_NAME))));
			return true;
		}
		else if(command.equals("exit") || command.equals("leave"))
		{
			//not a console command. only allow players
			if (denyConsole(sender))
				return true;
			
			Player player = (Player)sender;
			
			//make sure we're in the nether before porting/db modification
			if (player.getWorld().getName().equals(_config.getProperty(Configuration.Keys.NETHER_SERVER_NAME)))
			{
				player.teleport(Bukkit.getWorld("world").getSpawnLocation());
				_dbAccess.exitNether(player);
			}
			else
				sender.sendMessage("You must be in the nether world to be able to exit it O.o");
			
			return true;
		}
		else if (command.equals("info") || command.equals("stats") || command.equals("remaining"))
		{//output how much time they have left and how long to wait

			if (args.length == 1)
			{
				//not a console command. only allow players
				if (denyConsole(sender))
					return true;

				//output info for the player that called the command
				_dbAccess.outputInfo(sender, sender.getName());
			}
			else if (args.length == 2)
			{
				//make sure the user has access to view other peoples stats
				if (sender.hasPermission("Deity.nether.override"))
					_dbAccess.outputInfo(sender, args[1]);
				else //deny them access.  only allow admins to check other players stats
					sender.sendMessage("You do not have permissions to view another player's nether info.");
			}
			
			return true;
		}
		else if (command.equals("admin"))  
		{
			//all admin commands must have the override perm
			if (! sender.hasPermission("Deity.nether.override"))
			{
				sender.sendMessage("Sorry, you do not have appropriate permissions for that command");
				return true;
			}
			
			String subCommand = "help"; //default to help in case arg is missing
			
			//get the real command
			if (args.length >= 2)
			subCommand = args[1].toLowerCase();
			
			if (subCommand.equals("help") || subCommand.equals("?"))
			{
				outputUsageAdmin(sender);
				return true;
			}
			else if (subCommand.equals("list") || subCommand.equals("info"))
			{//sends to either console or player
				List<String> messages = new ArrayList<String>();
				messages.add(String.format("Amount of players in nether: %d", Bukkit.getWorld(_config.getProperty(Keys.NETHER_SERVER_NAME)).getPlayers().size()));
				messages.add(String.format("(admin worldReset ##) World lifespan in days: %s", _config.getProperty(Keys.DAYS_UNTIL_NETHER_RESET)));
				messages.add(String.format("(admin maxMinutes ##) Maximum minutes in nether: %s", _config.getProperty(Keys.MAX_SESSION_LENGTH)));
				messages.add(String.format("(admin sessionHours ##) Hours until session reset: %s", _config.getProperty(Keys.ENTRANCE_FREQUENCY)));
				messages.add(String.format("(admin monitor ##) MS rate to check players: %s", _config.getProperty(Keys.MONITOR_INTERVAL)));
				messages.add(String.format("(admin goldPercent ##) %% that gold nuggets drop: %s", _config.getProperty(Keys.PIGZOMBIE_GOLD_DROP_PERCENT)));
				sender.sendMessage(messages.toArray(new String[messages.size()]));
				return true;
			}
			else if (subCommand.equals("clear"))
			{//clear the session information for a specific user
				if (args.length == 3)
				{
					if (_dbAccess.clearSessionInfo(args[2]))
						sender.sendMessage(String.format("Successfully cleared session info for %s", args[2]));
					else
						sender.sendMessage(String.format("Failed to clear session info for %s", args[2]));
				}
				else //improper argument count. output the usage
				{
					outputUsageAdmin(sender);
				}
				return true;
			}
			else if (subCommand.equals("worldreset"))
			{
				if (args.length == 3)
				{
					int newVal = Integer.parseInt(args[2]);
					if (newVal <= 0) //disabling world resetting
					{
						sender.sendMessage("Automated nether world reset has been disabled");
						_config.setProperty(Keys.DAYS_UNTIL_NETHER_RESET, "0");
					}
					else
					{
						sender.sendMessage(String.format("Automated nether world reset will occur every %s days", args[2]));
						_config.setProperty(Keys.DAYS_UNTIL_NETHER_RESET, args[2]);
					}
				}
				else //improper argument count. output the usage
				{
					outputUsageAdmin(sender);
				}
				return true;
			}
			else if (subCommand.equals("maxminutes"))
			{
				if (args.length == 3)
				{
					int newVal = Integer.parseInt(args[2]);
					if (newVal <= 0) //invalid 
					{
						sender.sendMessage("Value must be greater than 0");
					}
					else
					{
						sender.sendMessage(String.format("Players can now remain in the nether for %s minutes", args[2]));
						_config.setProperty(Keys.MAX_SESSION_LENGTH, args[2]);
					}
				}
				else //improper argument count. output the usage
				{
					outputUsageAdmin(sender);
				}
				return true;
			}
			else if (subCommand.equals("sessionhours"))
			{
				if (args.length == 3)
				{
					int newVal = Integer.parseInt(args[2]);
					if (newVal <= 0) //invalid
					{
						sender.sendMessage("Value must be greater than 0");
					}
					else
					{
						sender.sendMessage(String.format("Minutes will be cleared after %s hours of nether inactivity", args[2]));
						_config.setProperty(Keys.ENTRANCE_FREQUENCY, args[2]);
					}
				}
				else //improper argument count. output the usage
				{
					outputUsageAdmin(sender);
				}
				return true;
			}
			else if (subCommand.equals("monitor"))
			{
				if (args.length == 3)
				{
					int newVal = Integer.parseInt(args[2]);
					if (newVal <= 0) //invalid
					{
						sender.sendMessage("Value must be greater than 0");
					}
					else
					{
						sender.sendMessage(String.format("The monitor interval will be set to %s after the next reboot", args[2]));
						_config.setProperty(Keys.MONITOR_INTERVAL, args[2]);
					}
				}
				else //improper argument count. output the usage
				{
					outputUsageAdmin(sender);
				}
				return true;
			}
			else if (subCommand.equals("goldpercent"))
			{
				if (args.length == 3)
				{
					int newVal = Integer.parseInt(args[2]);
					if (newVal <= 0) //disabling nugget drop
					{
						sender.sendMessage("Gold nugget drops from Pig Zombie have been disabled");
						_config.setProperty(Keys.PIGZOMBIE_GOLD_DROP_PERCENT, "0");
					}
					else if(newVal > 6400)
					{
						sender.sendMessage("Gold nugget drops cannot be set higher than 6400% since that is greater than one stack");
					}
					else
					{
						sender.sendMessage(String.format("Gold nugget drops set to %s%% per Pig Zombie kill", args[2]));
						_config.setProperty(Keys.PIGZOMBIE_GOLD_DROP_PERCENT, args[2]);
					}
				}
				else //improper argument count. output the usage
				{
					outputUsageAdmin(sender);
				}
				return true;
			}	
		}
		else if (command.equals("cheat"))   //just pretend this doesn't exist. only for testing
		{
			if (sender.getName().equalsIgnoreCase("btarb24"))
			{
				Player player = (Player)sender;
				//stuffs so i dont die
				player.getInventory().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
				player.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
				player.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
				player.getInventory().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
				player.getInventory().addItem(new ItemStack(Material.DIAMOND_SWORD), new ItemStack(Material.DIAMOND_PICKAXE), new ItemStack(Material.DIAMOND_AXE), new ItemStack(Material.DIAMOND_SPADE), new ItemStack(Material.COOKED_BEEF, 64));
				return true;
			}
		}
		
		//output the usage since we fell through the list
		outputUsage(sender);
		
		//always return true.. never output the default usage from yml
		return true;
	}
	
	private boolean denyConsole(CommandSender sender)
	{
		if (! (sender instanceof Player))
		{
			sender.sendMessage("This command is not available from the console");
			return true;
		}
		else
			return false;
	}

	private void outputUsage(CommandSender sender)
	{
		//output usage for regular users
		List<String> messages = new ArrayList<String>();
		messages.add("NETHER USAGE");
		messages.add("/nether enter");
		messages.add("/nether exit");
		messages.add("/nether info");
		sender.sendMessage(messages.toArray(new String[messages.size()]));
		
		//output usage for admin users
		if (sender.hasPermission("Deity.nether.override"))
		{
			sender.sendMessage("/nether info [playerName]");
			outputUsageAdmin(sender);
		}
	}
	
	//the usage for /nether admin
	private void outputUsageAdmin(CommandSender sender)
	{
		List<String> messages = new ArrayList<String>();
		messages.add("NETHER ADMIN USAGE");
		messages.add("/nether admin list");
		messages.add("/nether admin worldReset [days]");
		messages.add("/nether admin maxMinutes [minutes]");
		messages.add("/nether admin sessionHours [hours]");
		messages.add("/nether admin monitor [milliseconds]");
		messages.add("/nether admin goldPercent [0 - 6400]");
		messages.add("/nether admin clear [playerName]");
		sender.sendMessage(messages.toArray(new String[messages.size()]));
	}
}
