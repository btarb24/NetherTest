package com.github.btarb24.NetherTest;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class EvtHandler implements Listener
{
	NetherTest _nether;
	public EvtHandler(NetherTest nether)
	{
		nether.getServer().getPluginManager().registerEvents(this, nether);
		_nether = nether; 
	}
	
	@EventHandler
	public void OnPlayerDeath(PlayerDeathEvent event)
	{
		//check if they were in the nether.  kill their session if they were. 
		//they will respawn in main world and not be able to get back in nether
		if (event.getEntity().getWorld().getName().equalsIgnoreCase(NetherTest.NETHER_SERVER_NAME))
			_nether.endNetherSession(event.getEntity());
	}
	
	@EventHandler
	public void OnPlayerQuit(PlayerQuitEvent event)
	{
		//persists the player's used nether minutes if they logout while in nether
		if (event.getPlayer().getWorld().getName().equals(NetherTest.NETHER_SERVER_NAME))
			_nether.logoutWhileInNether(event.getPlayer());
	}
	
	@EventHandler
	public void OnPlayerJoin(PlayerJoinEvent event)
	{
		//handling this event so that we can check if they're in the nether. If so then
		//teleport them to main world.  
		//positive effect.. checking info in nether will not display skewed stats
		//negative effect.. the player will not be in the same nether location as when they logged out
		if (event.getPlayer().getWorld().getName().equals(NetherTest.NETHER_SERVER_NAME))
		{
			event.getPlayer().sendMessage(event.getPlayer().getWorld().getName());
			//punt them back to main world if they are out of time but somehow were still in nether.
			//odds of this are very low.  Teleporting them on join also causes an error and forces
			//them back to the server select screen.  Thankfully it's a super low chance situation
			//and that it's super easy to just double-click the server to log back in again.
			if (! _nether.hasTimeRemaining(event.getPlayer()))
			{
				event.getPlayer().sendMessage("teleport");
				event.getPlayer().teleport(Bukkit.getWorld("world").getSpawnLocation());
			}
		}
		
	}
}
