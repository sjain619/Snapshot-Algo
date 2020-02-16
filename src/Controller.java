import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;


public class Controller {

	private static Map<String, Bank.InitBranch.Branch> mapBranches;

	public static void main(String[] args) {
		try { 
			int totalAmt = Integer.parseInt(args[0]);
			File file = new File(args[1]);
			if (!file.exists()) {
				System.err.println("File with name: "+args[1]+"  doeen't exist.");
				return;
			}
			Bank.InitBranch.Builder initBranchBuilder = Bank.InitBranch.newBuilder();
			addBranches(file, initBranchBuilder);
			int initialAmt = totalAmt / initBranchBuilder.getAllBranchesCount();

			if (totalAmt % initBranchBuilder.getAllBranchesCount() != 0) {
				System.err.println("Total amount is assumed to be divisible by number of branches..");
				return;
			}
			Controller controller = new Controller();
			controller.initBranch(initBranchBuilder, initialAmt);
			int count = 100;
			if(args.length>2)
				count = Integer.parseInt(args[2]);
			for (int i = 1; i <= count; i++) {
				startSnapshot(initBranchBuilder, i);
			}
		} catch(UnknownHostException e) {
			e.printStackTrace();
		}catch(ArrayIndexOutOfBoundsException e) {
			System.err.println("Please enter : total amount and branches.txt");
		}
		catch (IOException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		} catch (NumberFormatException e) {
			System.err.println("Total amount should be an Integer value.");
			e.printStackTrace();
		}
	}

	private static BufferedReader addBranches(File file, Bank.InitBranch.Builder initBranchBuilder)
			throws FileNotFoundException, IOException {
		BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
		String l = null;
		while ((l = bufferedReader.readLine()) != null) {
			String[] str = l.split(" ");
			String name = str[0].trim();
			String ip = str[1].trim();
			int port = Integer.parseInt(str[2].trim());
			Bank.InitBranch.Branch.Builder branch = Bank.InitBranch.Branch.newBuilder();
			branch.setName(name);
			branch.setIp(ip);
			branch.setPort(port);
			initBranchBuilder.addAllBranches(branch);
		}
		bufferedReader.close();
		return bufferedReader;
	}

	private void initBranch(Bank.InitBranch.Builder initBranchBuilder, int initialAmt) throws UnknownHostException, IOException {
		for (Bank.InitBranch.Branch branch : initBranchBuilder.getAllBranchesList()) {
			initBranchBuilder.setBalance(initialAmt);
			String ip = branch.getIp();
			int port = branch.getPort();
			Socket socket = new Socket(ip, port);
			Bank.BranchMessage.Builder messageBranch = Bank.BranchMessage.newBuilder();
			messageBranch.setInitBranch(initBranchBuilder);
			Bank.BranchMessage branchMessage = messageBranch.build();
			branchMessage.writeDelimitedTo(socket.getOutputStream());
			socket.close();
		}
	}

	private static void startSnapshot(Bank.InitBranch.Builder initBranchBuilder, int snapshotId) {
		try {
			Thread.sleep(1000);
			List<Bank.InitBranch.Branch> initList = initBranchBuilder.getAllBranchesList();
			Bank.InitBranch.Branch branch = initList.get(new Random().nextInt(initBranchBuilder.getAllBranchesCount()));
			Bank.InitSnapshot.Builder initSnapshotBuilder = Bank.InitSnapshot.newBuilder();
			initSnapshotBuilder.setSnapshotId(snapshotId);
			Bank.InitSnapshot initSnap = initSnapshotBuilder.build();
			Bank.BranchMessage.Builder msgBuilder = Bank.BranchMessage.newBuilder();
			Bank.BranchMessage branchMessage = msgBuilder.setInitSnapshot(initSnap).build();
			Socket socket = new Socket(branch.getIp(), branch.getPort());
			branchMessage.writeDelimitedTo(socket.getOutputStream());
			retrieveSnapshot(initBranchBuilder, snapshotId);
			socket.close();
		} catch (Exception ex) {
			System.err.println(ex.getMessage());
			ex.printStackTrace();
		}
	}

	private static void retrieveSnapshot(Bank.InitBranch.Builder initBranchBuilder, int snapshotId) {
		try {
			Thread.sleep(5000);
			Bank.RetrieveSnapshot retrieveSnapshot = Bank.RetrieveSnapshot.newBuilder().setSnapshotId(snapshotId).build();
			Bank.BranchMessage branchMessage = Bank.BranchMessage.newBuilder().setRetrieveSnapshot(retrieveSnapshot).build();

			System.out.println("\t snapshot_id : " + snapshotId + "\n");

			int totalbalance = 0;
			mapBranches = new ConcurrentHashMap<>();
			populateMap(initBranchBuilder);
			totalbalance = iterateBranches(branchMessage, totalbalance);
			System.out.println("Total amount in Distributed Bank : " + totalbalance);
		} catch (Exception e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		}
	}

	private static int iterateBranches(Bank.BranchMessage branchMessage, int totalbalance)
			throws UnknownHostException, IOException {
		for (Bank.InitBranch.Branch initBranch : mapBranches.values()) {
			Socket socket = new Socket(initBranch.getIp(), initBranch.getPort());
			branchMessage.writeDelimitedTo(socket.getOutputStream());
			InputStream inputStream = socket.getInputStream();
			Bank.BranchMessage message = Bank.BranchMessage.parseDelimitedFrom(inputStream);
			Bank.ReturnSnapshot.LocalSnapshot localSnapshot = message.getReturnSnapshot().getLocalSnapshot();
			if (localSnapshot != null) {
				totalbalance += localSnapshot.getBalance();
				System.out.print(initBranch.getName() + " Balance : " + localSnapshot.getBalance()+",\t ");
				int i = 0;
				List<String> branchNameList = new ArrayList<String>();
				for (Bank.InitBranch.Branch branch : mapBranches.values()) {
					if (!initBranch.getName().equalsIgnoreCase(branch.getName()))
						branchNameList.add(branch.getName());
				}
				for (int j : localSnapshot.getChannelStateList()) {
					totalbalance += j;
					System.out.print(branchNameList.get(i) + "->" + initBranch.getName() + " : " + j+",\t ");
					i++;
				}
				System.out.println("\n");
				socket.close();
			}
		}
		return totalbalance;
	}

	private static void populateMap(Bank.InitBranch.Builder initBranchBuilder) {
		for (int i = 0; i < initBranchBuilder.getAllBranchesList().size(); i++) {
			mapBranches.put(initBranchBuilder.getAllBranches(i).getName(), initBranchBuilder.getAllBranches(i));
		}
	}
}