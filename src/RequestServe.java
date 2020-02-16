import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class RequestServe {

	private boolean isAccept;
	private List<Integer> incomingTransList;
	private Bank.InitBranch.Branch branch;

	private static Lock mutex = new ReentrantLock();

	public RequestServe() {

	}

	public RequestServe(Bank.InitBranch.Branch branch) {
		incomingTransList = new ArrayList<>();
		setAccept(true);
		this.branch = branch;
	}

	public boolean isAccept() {
		return isAccept;
	}

	public synchronized void setAccept(boolean accept) {
		this.isAccept = accept;
	}

	public synchronized void add(int amount) {
		if (isAccept())
			incomingTransList.add(amount);
	}

	public void initReqTransfer(Branch branch, RequestServe requestServe) {
		while (true) {
			try {
				Thread.sleep(new Random().nextInt(branch.getinterval()));
				mutex.lock();
				int balance = branch.getBalance();
				int amountToTransfer = (balance * requestServe.getRandomNumber()) / 100;

				if (balance - amountToTransfer > 0 && branch.getMapBranches() != null
						&& !branch.getMapBranches().isEmpty()) {

					String branchName = requestServe.getRandomBranch(branch.getMapBranches());

					if (!branch.getbranchName().equalsIgnoreCase(branchName)) {

						Bank.Transfer.Builder transferBuilder = getBuilder(branch, amountToTransfer, branchName);

						Bank.BranchMessage branchMessage = Bank.BranchMessage.newBuilder().setTransfer(transferBuilder)
								.build();
						try {
							if(branch.getinterval() >=1000)
								System.out.println("Sending $"+ branchMessage.getTransfer().getAmount()+" to "+branchMessage.getTransfer().getRecvBranch());
							branch.withdrawBalance(amountToTransfer);
							balance = branch.getBalance();
							Socket socket = new Socket(branch.getMapBranches().get(branchName).getIp(),
									branch.getMapBranches().get(branchName).getPort());

							branchMessage.writeDelimitedTo(socket.getOutputStream());
							socket.getOutputStream().write(branch.getbranchName().getBytes());
							socket.close();
						} catch (IOException e) {
							System.err.println("Failed in money transfer" + e.getMessage());
							e.printStackTrace();
						}
					}
				}
				mutex.unlock();

			} catch (InterruptedException ex) {
				System.err.println("Failed in money transfer" + ex.getMessage());
				ex.printStackTrace();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	private Bank.Transfer.Builder getBuilder(Branch branch, int amountToTransfer, String branchName) {
		Bank.Transfer.Builder transferBuilder = Bank.Transfer.newBuilder();
		transferBuilder.setSendBranch(branch.getbranchName());
		transferBuilder.setRecvBranch(branchName);
		transferBuilder.setAmount(amountToTransfer);
		transferBuilder.build();
		return transferBuilder;
	}

	public void handleReceive(InputStream inputStream, Bank.BranchMessage branchMessage, Branch branch,
			RequestServe requestServe) {
		try {
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
			String branchName = bufferedReader.readLine();
			mutex.lock();
			if(branch.getinterval() >=1000)
				System.out.println("Receiving $"+ branchMessage.getTransfer().getAmount()+" from "+branchMessage.getTransfer().getSendBranch());
			branch.addBalance(branchMessage.getTransfer().getAmount());
			createLocalSnapshot(branchName, branchMessage.getTransfer().getAmount(), branch);
			mutex.unlock();
			bufferedReader.close();
		} catch (IOException ex) {
			ex.printStackTrace();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private static void createLocalSnapshot(String branchName, int amount, Branch branch) {
		RequestServe requestServe = null;
		if (branch.getChannelLocalSnapshotMap() != null) {
			for (int snapshotId : branch.getChannelLocalSnapshotMap().keySet()) {
				requestServe = branch.getChannelLocalSnapshotMap().get(snapshotId).get(branchName);
				requestServe.add(amount);
			}
		}

	}

	public void initSnapshot(Bank.BranchMessage branchMessage, Branch branch) {
		Bank.InitSnapshot intiSnapShot = branchMessage.getInitSnapshot();
		try {
			if (branch.getlocalSnapshotMap() != null
					&& !branch.getlocalSnapshotMap().containsKey(intiSnapShot.getSnapshotId())) {
				mutex.lock();

				Bank.ReturnSnapshot.LocalSnapshot.Builder localSnapshotBuilder = Bank.ReturnSnapshot.LocalSnapshot
						.newBuilder();
				localSnapshotBuilder.setSnapshotId(intiSnapShot.getSnapshotId());
				localSnapshotBuilder.setBalance(branch.getBalance());

				Bank.ReturnSnapshot.LocalSnapshot localSnapshot = localSnapshotBuilder.build();
				branch.getlocalSnapshotMap().put(intiSnapShot.getSnapshotId(), localSnapshot);

				processLocalChannel(branch, intiSnapShot.getSnapshotId());
			}
			sendMarkers(intiSnapShot.getSnapshotId(), branch);
			mutex.unlock();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private void processLocalChannel(Branch branch, int snapshotId) {
		if (!branch.getChannelLocalSnapshotMap().containsKey(snapshotId)) {

			Map<String, RequestServe> requestServeMap = new HashMap<String, RequestServe>();

			for (Bank.InitBranch.Branch initBranch : branch.getMapBranches().values()) {
				if (!initBranch.getName().equalsIgnoreCase(branch.getbranchName()))
					requestServeMap.put(initBranch.getName(), new RequestServe(initBranch));
			}
			branch.getChannelLocalSnapshotMap().put(snapshotId, requestServeMap);
		}
	}

	public void receiveMarkers(Bank.BranchMessage branchMessage, InputStream inputStream, Branch branch) {
		try {

			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
			String branchName = bufferedReader.readLine();

			Bank.Marker marker = branchMessage.getMarker();
			if (branch.getlocalSnapshotMap() != null
					&& !branch.getlocalSnapshotMap().containsKey(marker.getSnapshotId())) {
				mutex.lock();
				Bank.ReturnSnapshot.LocalSnapshot.Builder localSnapshotBuilder = Bank.ReturnSnapshot.LocalSnapshot
						.newBuilder();
				localSnapshotBuilder.setBalance(branch.getBalance());
				localSnapshotBuilder.setSnapshotId(marker.getSnapshotId());

				Bank.ReturnSnapshot.LocalSnapshot localSnapShot = localSnapshotBuilder.build();

				branch.getlocalSnapshotMap().put(marker.getSnapshotId(), localSnapShot);

				processLocalChannelRecMakers(branch, branchName, marker.getSnapshotId());
				if (getMarkerFlag(marker.getSnapshotId(), branch))
					sendMarkers(marker.getSnapshotId(), branch);

				mutex.unlock();
			} else {
				if (branch.getChannelLocalSnapshotMap() != null) {
					mutex.lock();
					Map<String, RequestServe> requestServeMap = branch.getChannelLocalSnapshotMap()
							.get(marker.getSnapshotId());
					RequestServe requestServe = requestServeMap.get(branchName);
					requestServe.setAccept(false);
					mutex.unlock();
				}

			}

		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	private void processLocalChannelRecMakers(Branch branch, String branchName, int snapshotId) {
		if (!branch.getChannelLocalSnapshotMap().containsKey(snapshotId)) {
			Map<String, RequestServe> requestServeMap = new HashMap<String, RequestServe>();

			for (Bank.InitBranch.Branch initBranch : branch.getMapBranches().values()) {

				if (!initBranch.getName().equalsIgnoreCase(branch.getbranchName())) {
					requestServeMap.put(initBranch.getName(), new RequestServe(initBranch));

					if (branchName.equalsIgnoreCase(initBranch.getName())) {
						requestServeMap.get(branchName).setAccept(false);
					}
				}
			}
			branch.getChannelLocalSnapshotMap().put(snapshotId, requestServeMap);
		}
	}

	private synchronized static void sendMarkers(int snapshotId, Branch branch) {
		for (String branchName : branch.getMapBranches().keySet()) {
			try {
				Bank.Marker.Builder marker = getMaker(snapshotId, branch, branchName);

				Bank.BranchMessage branchMessage = Bank.BranchMessage.newBuilder().setMarker(marker).build();
				Socket socket = null;
				if (!branchName.equalsIgnoreCase(branch.getbranchName())) {
					Bank.InitBranch.Branch initBranch = branch.getMapBranches().get(branchName);
					socket = new Socket(initBranch.getIp(), initBranch.getPort());
					branchMessage.writeDelimitedTo(socket.getOutputStream());
					socket.getOutputStream().write(branch.getbranchName().getBytes());
					socket.close();
				}
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}

	private static Bank.Marker.Builder getMaker(int snapshotId, Branch branch, String branchName) {
		Bank.Marker.Builder marker = Bank.Marker.newBuilder();
		marker.setSnapshotId(snapshotId);
		marker.setSendBranch(branch.getbranchName());
		marker.setRecvBranch(branchName);
		marker.build();
		return marker;
	}

	private synchronized static boolean getMarkerFlag(int snapShotId, Branch branch) {
		for (RequestServe channel : branch.getChannelLocalSnapshotMap().get(snapShotId).values())
			if (channel.isAccept())
				return true;
		return false;
	}

	public void retrieveSnapshot(Socket socket, Bank.BranchMessage branchMessage, InputStream inputStream,
			Branch branch) {
		try {
			int retrieveSnapshotId = branchMessage.getRetrieveSnapshot().getSnapshotId();
			if ((branch.getlocalSnapshotMap() != null && branch.getlocalSnapshotMap().containsKey(retrieveSnapshotId))
					&& (branch.getChannelLocalSnapshotMap() != null
							&& branch.getChannelLocalSnapshotMap().containsKey(retrieveSnapshotId))) {
				Bank.ReturnSnapshot.LocalSnapshot.Builder returnLocalSnapShotBuilder = Bank.ReturnSnapshot.LocalSnapshot
						.newBuilder();
				int localSnapShotBalance = branch.getlocalSnapshotMap().get(retrieveSnapshotId).getBalance();
				returnLocalSnapShotBuilder.setBalance(localSnapShotBalance);
				returnLocalSnapShotBuilder.setSnapshotId(retrieveSnapshotId);

				mutex.lock();

				Map<String, RequestServe> requestServeMap = branch.getChannelLocalSnapshotMap().get(retrieveSnapshotId);
				ConcurrentHashMap<String, RequestServe> concurrentHashMap = new ConcurrentHashMap<String, RequestServe>();
				concurrentHashMap.putAll(requestServeMap);
				for (String incomingBranchChannel : concurrentHashMap.keySet()) {
					RequestServe requestServe = concurrentHashMap.get(incomingBranchChannel);
					int sum = requestServe.getSumOfChannel();
					returnLocalSnapShotBuilder.addChannelState(sum);
				}
				Bank.ReturnSnapshot.LocalSnapshot localSnapshot = returnLocalSnapShotBuilder.build();
				branchMessage = Bank.BranchMessage.newBuilder()
						.setReturnSnapshot(Bank.ReturnSnapshot.newBuilder().setLocalSnapshot(localSnapshot)).build();
				mutex.unlock();
				branchMessage.writeDelimitedTo(socket.getOutputStream());
				socket.shutdownOutput();
				socket.close();

			}
		} catch (IOException ex) {
			ex.printStackTrace();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public synchronized int getRandomNumber() {
		return new Random().nextInt(5) + 1;
	}

	public synchronized String getRandomBranch(Map<String, Bank.InitBranch.Branch> branchesMap) {
		return new ArrayList<>(branchesMap.keySet()).get(new Random().nextInt(branchesMap.size()));
	}

	public synchronized int getSumOfChannel() {
		return incomingTransList.stream().reduce(0, Integer::sum);
	}

}