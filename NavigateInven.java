package com.sjsu.homework2;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.vmware.vim25.VirtualMachineCloneSpec;
import com.vmware.vim25.VirtualMachineConfigInfo;
import com.vmware.vim25.VirtualMachineMovePriority;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.VirtualMachineRelocateSpec;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;


public class NavigateInven {

    public static void main(String[] args) {
    	
        if(args.length!=4)
        {
            System.out.println("Error!!!!! In Arguments");
            System.exit(0);
        }
        String ip = args[0], username = args[1], password = args[2], vmFolderPath = args[3];
        int hostCount=0;
        Datacenter data = null;
        Folder vm_Folder = null;
        boolean is_Esxi=false;

        try {
            ServiceInstance service = new ServiceInstance(new URL("https://" + ip + "/sdk"), username, password, true);
            Folder root = service.getRootFolder();

            if(vmFolderPath.length()==1){
                
                is_Esxi=true;
                Folder root_folder = (Folder) service.getRootFolder();

            }
            else if (vmFolderPath.length()>1 && vmFolderPath.contains("/")) {
                StringBuilder path_new = new StringBuilder();
                String[] split_Slash = vmFolderPath.split("/");

                for (String s : split_Slash) {

                    path_new.append(s).append("/");
                    ManagedEntity entity = service.getSearchIndex().findByInventoryPath(path_new.toString());

                    if (entity.getMOR().getType().equals("Datacenter")) {
                        data = (Datacenter) entity;
                        path_new.append("vm/");
                    } else if (entity.getMOR().getType().equals("Folder")) {
                        vm_Folder = (Folder) entity;
                    }
                }
            }

            
            ManagedEntity[] me_hosts=null;
            if(is_Esxi)
                me_hosts=new InventoryNavigator(root).searchManagedEntities("HostSystem");
            else
                me_hosts= new InventoryNavigator(data).searchManagedEntities("HostSystem");
            for(ManagedEntity me:me_hosts){
                System.out.println("host["+hostCount+"]");
                System.out.println("Name: " + me.getName());
                System.out.println("Product Full Name: " + service.getAboutInfo().getFullName());
                hostCount++;
            }

            ManagedEntity[] v_Machines=null;
            if(is_Esxi)
                v_Machines= new InventoryNavigator(root).searchManagedEntities("VirtualMachine");
            else
                v_Machines= new InventoryNavigator(vm_Folder).searchManagedEntities("VirtualMachine");

            if (v_Machines == null ) {
                System.out.println("No Virtual Machine Found on Host");
                return;
            }

            int vm_count = 0;

            for(ManagedEntity ment: v_Machines){

                VirtualMachine vrm = (VirtualMachine)ment;

                if(!vrm.getConfig().template){

                    VirtualMachineConfigInfo vmInfo = vrm.getConfig();
                    System.out.println("vm["+vm_count+"]");
                    System.out.println("Name = " + vrm.getName());
                    System.out.println("Guest_OS = " + vmInfo.getGuestFullName());
                    System.out.println("Guest State = " + vrm.getGuest().guestState);
                    System.out.println("Power State = " + vrm.getSummary().runtime.powerState.name());
                    System.out.println("Host Name= "+vrm.getRuntime().getHost().getVal());

                   

                    if(true){

                        Date d = new Date();
                        String desc = "SnapShot taken at " + d.toString();
                        String t_name=vrm.getName()+"- snapshot";

                        Task snap_Task = vrm.createSnapshot_Task(t_name,desc, false,false);

                        if (snap_Task.waitForTask() == Task.SUCCESS ){
                            System.out.println("Snapshot: Status = " + Task.SUCCESS);
                        }
                    }

              
                    String VM_Host = vrm.getRuntime().getHost().getVal();
                    String VM_HostIP = "";

                    if(true){
                        String clone = vrm.getName() + "-clone";
                        Folder par = (Folder)vrm.getParent();


                        VirtualMachineCloneSpec vclone_Spec = new VirtualMachineCloneSpec();
                        VirtualMachineRelocateSpec vmRelocSpec = new VirtualMachineRelocateSpec();
                        vclone_Spec.setLocation(vmRelocSpec);
                        vclone_Spec.setPowerOn(false);
                        vclone_Spec.setTemplate(false);

                        Task cloneTask = vrm.cloneVM_Task(par, clone , vclone_Spec);

                        if(cloneTask.getTaskInfo().getState().toString().equals("error")){
                            System.out.println("Clone : Status = error");
                        }

                        else if(cloneTask.waitForTask() == Task.SUCCESS ){
                            System.out.println("Clone : Status = " + Task.SUCCESS);
                        }
                    }

                    
                    if(hostCount>1){

                        if(VM_Host.equals("host-34")){
                            VM_HostIP = "130.65.159.10";
                        }else if(VM_Host.equals("host-28")){
                            VM_HostIP = "130.65.159.11";
                        }
                        HostSystem new_Host = (HostSystem) new InventoryNavigator(root).searchManagedEntity("HostSystem",VM_HostIP);
                        Task migrate_Task = null;
                        if(vrm.getSummary().runtime.powerState.name().equals("poweredOff")){
                            migrate_Task = vrm.migrateVM_Task(vrm.getResourcePool(), new_Host,
                                    VirtualMachineMovePriority.highPriority,
                                    VirtualMachinePowerState.poweredOff);
                        }else if(vrm.getSummary().runtime.powerState.name().equals("poweredOn")){
                            migrate_Task = vrm.migrateVM_Task(vrm.getResourcePool(), new_Host,
                                    VirtualMachineMovePriority.highPriority,
                                    VirtualMachinePowerState.poweredOn);
                        }
                        if(migrate_Task.waitForTask() == Task.SUCCESS)
                        {
                            System.out.println("VM Migration: Status = " + Task.SUCCESS);
                        }
                        else
                        {
                            System.out.println("VMotion failed!");
                        }




                    }else{

                        System.out.println("Migration Skipped: Only one host");
                    }
					
					
                    SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

                    for(Task tasks:vrm.getRecentTasks()){
                        System.out.print("task: target = " + tasks.getTaskInfo().getEntityName()+", ");
                        System.out.print("op = " + tasks.getTaskInfo().name +", ");
                        System.out.print("startTime = " + df.format(tasks.getTaskInfo().startTime.getTime()) + ", ");
                        System.out.print("endTime = " + df.format(tasks.getTaskInfo().completeTime.getTime())+ ", ");
                        if(tasks.getTaskInfo().getState().toString().equals("error")){
                            System.out.print("Status = The operation is not supported on the object");
                        }
                        else {
                            System.out.print("Status = Completed");
                        }
                        System.out.println();
                    }

                    vm_count++;
                }

            }

            service.getServerConnection().logout();
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }catch (Exception e) {
            e.printStackTrace();
        }



    }

}
