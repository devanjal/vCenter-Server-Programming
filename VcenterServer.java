package com.sjsu.homework2;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

import com.vmware.vim25.*;
import com.vmware.vim25.mo.*;


public class VcenterServer {

	public static void main(String[] args) throws Exception
	{
		
		System.out.println("CMPE283 HW2 from DEVANJAL KOTIA");
				String ip = args[0];
				String username = args[1];
				String password = args[2]; 
				String vmName = args[3];
		
		URL url = new URL("https://" + ip + "/sdk");
		ServiceInstance service = new ServiceInstance(url,username,password,true);
		
		VirtualMachine vrm = (VirtualMachine)new InventoryNavigator(service.getRootFolder()).searchManagedEntity("VirtualMachine", vmName);
		
		if(vrm==null)
		{
			System.out.println("No VM " + vmName + " found");
			service.getServerConnection().logout();
			return;
		}	
		
		ManagedEntity[] me_hosts = new InventoryNavigator(service.getRootFolder()).searchManagedEntities("HostSystem");
		int i=0;
		for(ManagedEntity ment: me_hosts)
		{
			HostSystem host_syst = (HostSystem) ment;
 
			System.out.println("host[" + i + "]\nHost Name: " + host_syst.getName() + 
					"\nProduct Full Name: "  + host_syst.getConfig().getProduct().fullName);
			i++;	
		}
		
		String host_names;
		System.out.println("\nVirutal Machine");
		ManagedEntity[] vme = new InventoryNavigator(service.getRootFolder()).searchManagedEntities
						(new String[][] { {"VirtualMachine", "name" } }, true);
		ManagedEntity[] ho = new InventoryNavigator(service.getRootFolder()).searchManagedEntities
						(new String[][] {{"HostSystem", "name"}}, true);
		for(int j=0; j<vme.length; j++)
		{
			if(vme[j].getName().equalsIgnoreCase(vmName))
			{
				System.out.println("============VM==========");
				System.out.println("Name: " + vme[j].getName());
				VirtualMachineConfigInfo vminfo = ((VirtualMachine) vme[j]).getConfig();
				GuestInfo gInfo = ((VirtualMachine) vme[j]).getGuest();
				VirtualMachineRuntimeInfo vmRTInfo = ((VirtualMachine) vme[j]).getRuntime();

				System.out.println("Guest OS: " + vminfo.getGuestFullName());
				System.out.println("Guest State: " + gInfo.guestState);
				System.out.println("Power State: " + vmRTInfo.powerState);

				for(ManagedEntity me: me_hosts)
				{
					String ho_name = me.getName();
					HostSystem my_Host = (HostSystem) new InventoryNavigator(service.getRootFolder()).searchManagedEntity("HostSystem", ho_name);
					ManagedEntity[] host_Entities = new InventoryNavigator(my_Host).searchManagedEntities("VirtualMachine");

					for (int k = 0; k < host_Entities.length; k++) 
					{ 
						VirtualMachine vm1 = (VirtualMachine) host_Entities[k]; 
						if(vm1.getName().equalsIgnoreCase(vmName))
						{
							HostSystem host_syst = (HostSystem) me;
							host_names = host_syst.getName();
							System.out.println("Host: " + host_names);
						}
					}
				}
				break;
			}
		}
		
		
		String snapshotname = "Devanjal-ubuntu1604-048-snapshot";
		String desc = "HW2 Snapshot";
		String op = "create";	
		if("create".equalsIgnoreCase(op))
		{
			Task task = vrm.createSnapshot_Task(snapshotname, desc, false, false);

			if(task.waitForMe()==Task.SUCCESS)	
			{  
				System.out.println("Snapshot Status: " + task.getTaskInfo().state);			
			}
			else
			{
				System.out.println("Snapshot Status: " +task.getTaskInfo().state );
				System.out.println("Status: Failed");
			}
		}
		String clone_Name = vmName+"-clone";
		// Clone
		VirtualMachineCloneSpec cloneSpec =  new VirtualMachineCloneSpec();
		cloneSpec.setLocation(new VirtualMachineRelocateSpec());
		cloneSpec.setPowerOn(false);
		cloneSpec.setTemplate(false);

		Task task1 = vrm.cloneVM_Task((Folder) vrm.getParent(), clone_Name, cloneSpec);
		try
		{ 
			if(task1.waitForMe()==Task.SUCCESS)
			{
				TaskInfo tInfo = task1.getTaskInfo();
				System.out.println("Clone: status : " + tInfo.getState());

			}
			else
			{
				System.out.println("Cloning Failed!!!");
			}
		}
		catch(Exception e){
			System.out.println("Clone Status: error");
		}
		int flag = 0;
		int len = me_hosts.length;
		for(int m=0; m<me_hosts.length; m++) 
		{		
			if(me_hosts.length > 1)
			{
				String host_Name = me_hosts[m].getName();
				System.out.println(host_Name);
				HostSystem myHost = (HostSystem) new InventoryNavigator(service.getRootFolder()).searchManagedEntity("HostSystem", host_Name);
				ManagedEntity[] hostSpecificEntities = new InventoryNavigator(myHost).searchManagedEntities("VirtualMachine");

				for (int j = 0; j < hostSpecificEntities.length; j++) 
				{ 
					VirtualMachine vm1 = (VirtualMachine) hostSpecificEntities[j]; 

					if(vm1.getName().equalsIgnoreCase(vmName))   
					{
						HostSystem host_syst = (HostSystem) me_hosts[m];
						host_names = host_syst.getName();
						
				
						int tar_Index;
						while(true)
						{
							Random r = new Random();
							int index = r.nextInt(len);
							if(host_names != me_hosts[index].getName())
							{
								tar_Index = index;
								break;
							}
						}
						
							String migratedHostName;
							migratedHostName = me_hosts[tar_Index].getName();
							HostSystem newHost = (HostSystem) new InventoryNavigator(service.getRootFolder()).searchManagedEntity(
									"HostSystem", migratedHostName);

							try{

								if(vrm.getRuntime().getPowerState() == VirtualMachinePowerState.poweredOn)
								{

									Task task2 = vrm.migrateVM_Task(vrm.getResourcePool(), newHost, VirtualMachineMovePriority.highPriority, 
											VirtualMachinePowerState.poweredOn);

									if(task2.waitForMe()==Task.SUCCESS)
									{
										TaskInfo tInfo = task2.getTaskInfo();
										System.out.println("Migration from " + host_names +" to " + migratedHostName + ": status : " + tInfo.getState());
										flag = 1;
									}

									else
									{
										System.out.println("Hot/Live Migration Failed!!!");
										TaskInfo info = task2.getTaskInfo();
										System.out.println(info.getError().getFault());

									}
								}
								else if(vrm.getRuntime().getPowerState() == VirtualMachinePowerState.poweredOff)
								{
									Task task2 = vrm.migrateVM_Task(vrm.getResourcePool(), newHost, VirtualMachineMovePriority.highPriority, 
											VirtualMachinePowerState.poweredOff);

									if(task2.waitForMe()==Task.SUCCESS)
									{
										TaskInfo tInfo = task2.getTaskInfo();
										System.out.println("Migration from " + host_names +" to " + migratedHostName + ": status : " + tInfo.getState());
										flag = 1;
									}

									else
									{
										System.out.println("Cold Migration Failed!!!");
										TaskInfo info = task2.getTaskInfo();
										System.out.println(info.getError().getFault());

									}
								}

							}
							catch(Exception e)
							{
								System.out.println("Migration skipped: only single ESXi host");
							}
					}
				}
				if(flag == 1)
				{
					break;
				}
			}
			else
			{
				System.out.println("Migration skipped: only single ESXi host");
			}
		}
		TaskManager task_Mgr = service.getTaskManager();
		SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		for(Task tasks:vrm.getRecentTasks())
		{
			System.out.print("task: target = " + tasks.getTaskInfo().getEntityName()+", ");
			System.out.print("op = " + tasks.getTaskInfo().name +", ");
			System.out.print("start_Time = " + df.format(tasks.getTaskInfo().startTime.getTime()) + ", ");
			System.out.print("end_Time = " + df.format(tasks.getTaskInfo().completeTime.getTime())+ ", ");
			if(tasks.getTaskInfo().getState().toString().equals("error"))
			{
				System.out.print("Status : The operation is not supported on the object");	
			}
			else 
			{
				System.out.print("Status : Completed");	
			}
			System.out.println();
		}
		service.getServerConnection().logout();
	}
}
