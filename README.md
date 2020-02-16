# cs457-557-f19-pa3-sjain13
cs457-557-f19-pa3-sjain13 created by GitHub Classroom
Programming language used: Java

To compile the code on remote.cs.binghamton.edu computers execute the following commands:

1.) To generate Bank.java, go to the project folder and execute:

$> bash
$> export PATH=/home/yaoliu/src_code/local/bin:$PATH
$> protoc --java_out=./src/ ./src/bank.proto

2.) To generate executables for branch and controller:

$> make

3.) To create branches on different port:

$> ./branch.sh branch1 9090 100

where first argument is executable shell file, second is the name of branch(should be the same in local file), third is the port and last is the maximum interval, in milliseconds, between Transfer messages.

4.) To execute the controller for the branches:

$> ./controller.sh 4000 branches.txt

where first argument is executable shell file, second is the the total amount of money in the distributed bank(which should be an integer and divisible by total number of branches.) and third is a local file that stores the names, IP addresses, and port numbers of all branches.

For example, if four branches with names: “branch1”, “branch2”, “branch3”, and “branch4” are running on
remote01.cs.binghamton.edu port 9090, 9091, 9092, and 9093, then branches.txt should contain:
branch1 128.226.114.201 9090
branch2 128.226.114.201 9091
branch3 128.226.114.201 9092
branch4 128.226.114.201 9093

We will receive output in the following format:

snapshot_id : 1

branch3 Balance : 1009,  branch2->branch3 : 0,   branch4->branch3 : 0,   branch1->branch3 : 0,

branch2 Balance : 984,   branch3->branch2 : 0,   branch4->branch2 : 0,   branch1->branch2 : 0,

branch4 Balance : 924,   branch3->branch4 : 0,   branch2->branch4 : 0,   branch1->branch4 : 0,

branch1 Balance : 1036,  branch3->branch1 : 20,  branch2->branch1 : 0,   branch4->branch1 : 27,

Total amount in Distributed Bank : 4000
         snapshot_id : 2

branch3 Balance : 1146,  branch2->branch3 : 0,   branch4->branch3 : 0,   branch1->branch3 : 18,

branch2 Balance : 991,   branch3->branch2 : 0,   branch4->branch2 : 101, branch1->branch2 : 8,

branch4 Balance : 839,   branch3->branch4 : 0,   branch2->branch4 : 0,   branch1->branch4 : 0,

branch1 Balance : 887,   branch3->branch1 : 10,  branch2->branch1 : 0,   branch4->branch1 : 0,

Total amount in Distributed Bank : 4000
         snapshot_id : 3

branch3 Balance : 1086,  branch2->branch3 : 47,  branch4->branch3 : 0,   branch1->branch3 : 0,

branch2 Balance : 1025,  branch3->branch2 : 0,   branch4->branch2 : 28,  branch1->branch2 : 0,

branch4 Balance : 944,   branch3->branch4 : 0,   branch2->branch4 : 0,   branch1->branch4 : 0,

branch1 Balance : 870,   branch3->branch1 : 0,   branch2->branch1 : 0,   branch4->branch1 : 0,

Total amount in Distributed Bank : 4000

Completion status: The assignment has been completed.


