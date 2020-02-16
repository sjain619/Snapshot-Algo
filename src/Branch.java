import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Branch {

	private String branchName;
	private int interval;

	private volatile Integer balance;

	private Map<String, Bank.InitBranch.Branch> mapBranches;
	private ConcurrentMap<Integer, Map<String, RequestServe>> channelLocalSnapshotMap;
	private ConcurrentHashMap<Integer, Bank.ReturnSnapshot.LocalSnapshot> localSnapshotMap;

	public static void main(String[] args) {

		try {
			String ip = InetAddress.getLocalHost().getHostAddress();
			System.out.println(
					"Branch: " + args[0] + " running on IP Address " + ip + " and Port " + Integer.parseInt(args[1]));
			ServerSocket serverSocket = new ServerSocket(Integer.parseInt(args[1]));
			Branch branch = new Branch(args[0], Integer.parseInt(args[2]));
			while (true) {
				Socket socket = serverSocket.accept();
				InputStream inputStream = socket.getInputStream();
				Bank.BranchMessage branchMessage = Bank.BranchMessage.parseDelimitedFrom(inputStream);
				setBranchState(branch, branchMessage, socket, inputStream);
			}
		} catch (ArrayIndexOutOfBoundsException ex) {
			System.err.println("Please enter branch name, port no. and timeInterval");
		} catch (IOException ex) {
			ex.printStackTrace();
		}

	}

	private static void setBranchState(Branch branch, Bank.BranchMessage branchMessage, Socket socket,
			InputStream inputStream) {
		try {
			RequestServe requestServe = new RequestServe();

			if (branch.getMapBranches().isEmpty() && branchMessage.getInitBranch().getAllBranchesCount() > 0
					&& branchMessage.hasInitBranch()) {

				branch.setBalance(branchMessage.getInitBranch().getBalance());

				for(Bank.InitBranch.Branch br: branchMessage.getInitBranch().getAllBranchesList())
					branch.getMapBranches().put(br.getName(),br);
				Thread thread = new Thread(() -> {
					requestServe.initReqTransfer(branch, requestServe);
				});
				new Thread(thread).start();
			} else if (branchMessage.hasRetrieveSnapshot())
				requestServe.retrieveSnapshot(socket, branchMessage, inputStream, branch);
			else if (branchMessage.hasTransfer()) {
				Thread.sleep(500);
				requestServe.handleReceive(inputStream, branchMessage, branch, requestServe);
			} else if (branchMessage.hasInitSnapshot())
				requestServe.initSnapshot(branchMessage, branch);
			else if (branchMessage.hasMarker())
				requestServe.receiveMarkers(branchMessage, inputStream, branch);
			if(branch.getinterval() >=1000)
			System.out.println("Updated balance of "+branch.getbranchName()+": $"+branch.getBalance());
		} catch (InterruptedException ex) {
			ex.printStackTrace();
		}
	}

	public Branch(String branchName, int interval) {
		this.branchName = branchName;
		this.interval = interval;
		mapBranches = new ConcurrentHashMap<>();
		localSnapshotMap = new ConcurrentHashMap<>();
		channelLocalSnapshotMap = new ConcurrentHashMap<>();
	}

	public synchronized int getBalance() {
		return this.balance;
	}

	public synchronized void setBalance(int balance) {
		this.balance = balance;
	}

	public synchronized void addBalance(int amount) {
		this.balance += amount;
	}

	public synchronized void withdrawBalance(int amount) {
		if (this.balance > amount)
			this.balance -= amount;
		else
			System.out.println("Not Sufficient balance.");
	}

	public String getbranchName() {
		return branchName;
	}

	public int getinterval() {
		return interval;
	}

	public Map<String, Bank.InitBranch.Branch> getMapBranches() {
		return mapBranches;
	}

	public ConcurrentMap<Integer, Map<String, RequestServe>> getChannelLocalSnapshotMap() {
		return channelLocalSnapshotMap;
	}

	public ConcurrentHashMap<Integer, Bank.ReturnSnapshot.LocalSnapshot> getlocalSnapshotMap() {
		return localSnapshotMap;
	}

}