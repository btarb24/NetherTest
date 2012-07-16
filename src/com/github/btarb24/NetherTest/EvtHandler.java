package com.github.btarb24.NetherTest;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

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
		if (event.getEntity().getWorld().getName().equalsIgnoreCase("world_nether"))
			_nether.EndNetherSession(event.getEntity());
	}
}
