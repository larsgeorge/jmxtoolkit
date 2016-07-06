==============
The JMXToolkit
==============

The JMXToolkit is a single, swiss-army-knife type class that can help you create
specific configuration files to query many hosts subsequently and either gather
the values of the various JMX attributes and operations these servers expose or
to run Nagios type checks on them.

For the command line parameters run the tool with -h as the sole option::

    $ java com.larsgeorge.jmxtoolkit.JMXToolkit -h

    Usage: JMXToolkit [-a <action>] [-c <user>] [-p <password>] [-u url] [-f <config>] [-o <object>]
     [-e regexp] [-i <extends>] [-q <attr-oper>] [-w <check>] [-m <message>] [-x] [-l] [-v] [-h]

        -a <action>	Action to perform, can be one of the following (default: query)

                create	Scan a JMX object for available attributes
                query	Query a set of attributes from the given objects
                check	Checks a given value to be in a valid range (see -w below)
                encode	Helps creating the encoded messages (see -m and -w below)
                walk	Walk the entire remote object list

        -c <user>	The user role to authenticate with (default: controlRole)
        -p <password>	The password to authenticate with (default: password)
        -u <url>	The JMX URL (default: service:jmx:rmi:///jndi/rmi://localhost:10001/jmxrmi)
        -f <config>	The config file to use (default: none)
        -o <object>	The JMX object query (default: none)
        -e <regexp>	The regular expression to match (default: none)
        -i <extends>	The name of the object that is inherited from (default: none)
        -q <attr-oper>	The attribute or operation to query (default: none)
        -w <check>	Used with -a check to define thresholds (default: none)

            Format: <ok-exitcode>[:<ok-message>] \
                    |<warn-exitcode>:[<warn-message>]:<warn-value>[:<warn-comparator>] \
                    [|<error-exitcode>:[<error-message>]:<error-value>[:<error-comparator>]]

            Example for Nagios and DFS used (in %):

                    0:OK%3A%20%7B0%7D|2:WARN%3A%20%7B0%7D:80:>=|1:FAIL%3A%20%7B0%7D:95:>

            Notes: Messages are URL-encoded to allow for any character being used. The current value
                   can be placed with {0} in the message. Allowed comparators: <,<=,=,==,>=,>

        -m <message>	The message to encode for further use (default: none)
        -x		Output config to console (do not write back to -f <config>)
        -l		Ignore missing attributes, do not throw an error
        -v		Verbose output
        -h		Prints this help

You can run ad-hoc queries specifying the required parameters on the command
line or make use of a specific properties file that let's you predefine all
hosts, objects, attributes, checks and so on.

Command Line
============

There are five types of actions: "create", "query", "check", "encode, "and "walk".
The "create" and "walk" actions are used to create properties files and are
explained in the next section. The other three are explained here but can be used
in combination with a properties file and examples of such use are also shown in the
next section.

Query
-----

This action allows to simply query a specific or all attributes of a JMX enabled
host. Examples:

- Retrieve all values for a specific object::

    $ java com.larsgeorge.jmxtoolkit.JMXToolkit \
      -u "service:jmx:rmi:///jndi/rmi://master.foobar.com:10001/jmxrmi" \
      -o "hadoop:name=FSNamesystemState,service=NameNode"

    CapacityTotal:37602137948160 CapacityUsed:20282880688128 CapacityRemaining:16583540920320 TotalLoad:21 BlocksTotal:214256 FilesTotal:405787
    PendingReplicationBlocks:0 UnderReplicatedBlocks:0 ScheduledReplicationBlocks:0 FSState:Operational numLiveDataNodes:20 numDeadDataNodes:0

Note: "-a query" is the default and therefore was omitted from the commands above.

- Retrieve one specific value only::

    $ java com.larsgeorge.jmxtoolkit.JMXToolkit \
      -u "service:jmx:rmi:///jndi/rmi://master.foobar.com:10001/jmxrmi" \
      -o "hadoop:name=FSNamesystemState,service=NameNode" -q CapacityRemaining

    CapacityRemaining:16583540879360

An operation is distinguished from an attribute by a leading star symbol. This
is also how they are distinguished in the properties files (see below)::

    $ java com.larsgeorge.jmxtoolkit.JMXToolkit \
      -u "service:jmx:rmi:///jndi/rmi://master.foobar.com:10001/jmxrmi" \
      -o "hadoop:name=FSNamesystemState,service=NameNode" -q *numLiveDataNodes

    numLiveDataNodes:20

Check
-----

A check is a Nagios type check where usually a single value of a JMX attribute
or operation is compared to a threshold value. If the value is higher than the
threshold a specific exit code is used to indicate a warning or even an error
state. These exit codes are used by tools like Nagios to check if a value is OK
or not. Examples::

    $ java com.larsgeorge.jmxtoolkit.JMXToolkit \
      -u "service:jmx:rmi:///jndi/rmi://master.foobar.com:10001/jmxrmi" \
      -o "hadoop:name=FSNamesystemState,service=NameNode" -q CapacityRemaining \
      -w "0|2::16583540879360:<|1::8291770439680:<" -v

    Reading properties...
    Action -> check
    Checking value...
    Querying values...
    Details -> CapacityRemaining, value=16583540760576
    Check -> |0|2::16583540879360:<|1::8291770439680:<
    Exit code -> 2
    Done.

Note: The use of the -v verbose option defies the intended use of a check as it
outputs more than just the exit codes, but it is useful to test the command and
hence its use in this example.

Optionally a message can be specified that is output on the console, and which
is typically used and displayed by tools like Nagios::

    $ java com.larsgeorge.jmxtoolkit.JMXToolkit \
      -u "service:jmx:rmi:///jndi/rmi://master.foobar.com:10001/jmxrmi" \
      -o "hadoop:name=FSNamesystemState,service=NameNode" -q CapacityRemaining \
      -w "0:OK%3A%20%7B0%7D|2:WARN%3A%20%7B0%7D:16583540879360:<|1:FAILED%3A%20%7B0%7D:8291770439680:<"

    WARN: 16,583,539,212,288

The message is URL-encoded (see "Encode" action described below) and uses Java's
MessageFormat class to format the message. A place-holder can be used to put the
current value into the message, for example "CapacityRemaining OK: Remaining:{0}".
This also allows to specify different number format patterns, for example
"{0,number,#}" resulting in::

    $ java com.larsgeorge.jmxtoolkit.JMXToolkit \
      -u "service:jmx:rmi:///jndi/rmi://master.foobar.com:10001/jmxrmi" \
      -o "hadoop:name=FSNamesystemState,service=NameNode" -q CapacityRemaining \
      -w "0|2:WARN%3A+%7B0%2Cnumber%2C%23%7D:16583540879360:<|1:FAILED%3A+%7B0%2Cnumber%2C%23%7D:8291770439680:<"

    WARN: 16583539253248

Encode
------

This action is to help create the appropriate messages for the checks explained
above. Example::

    $ java com.larsgeorge.jmxtoolkit.JMXToolkit -a encode -m "OK: Current {0}"
    OK%3A+Current+%7B0%7D

Properties File
===============

The use of properties files is twofold, first it allows to specify many nodes
and their values in one place. Secondly it saves a retrieval step which is
needed for ad-hoc queries without the "-q" option as shown above.

Walk
----

The walk action allows to quickly discover all available objects a JMX enabled
server provides. With that it is much easier to create the properties file
discussed next.

Example::

    $ java com.larsgeorge.jmxtoolkit.JMXToolkit -a walk
    -u "service:jmx:rmi:///jndi/rmi://master.foobar.com:10001/jmxrmi" | grep object

    object -> java.lang:name=CMS Old Gen,type=MemoryPool
    object -> java.lang:type=Memory
    object -> hadoop:name=RpcActivityForPort9000,service=NameNode
    object -> java.lang:name=Copy,type=GarbageCollector
    object -> hadoop:name=FSNamesystemState,service=NameNode
    object -> java.lang:name=Code Cache,type=MemoryPool
    object -> java.lang:type=Runtime
    object -> java.lang:type=ClassLoading
    object -> java.lang:type=Threading
    object -> java.lang:name=ConcurrentMarkSweep,type=GarbageCollector
    object -> hadoop:name=NameNodeActivity,service=NameNode
    object -> java.util.logging:type=Logging
    object -> java.lang:type=Compilation
    object -> java.lang:name=Eden Space,type=MemoryPool
    object -> com.sun.management:type=HotSpotDiagnostic
    object -> java.lang:name=Survivor Space,type=MemoryPool
    object -> java.lang:name=CMS Perm Gen,type=MemoryPool
    object -> java.lang:type=OperatingSystem
    object -> java.lang:name=CodeCacheManager,type=MemoryManager
    object -> JMImplementation:type=MBeanServerDelegate

Further filtering out the required objects names allows to find the required section
names explained next (see @object keys below).

Create
------

It does make sense to create the properties file in two steps. First is to
create a template that defines the various values for each host and JMX object.

One way is to use the "walk" action explained above and then feed a section name into
the "create" action like so::

    $ java com.larsgeorge.jmxtoolkit.JMXToolkit -a create
    -u "service:jmx:rmi:///jndi/rmi://master.foobar.com:10001/jmxrmi"
    -o hadoop:name=RpcActivityForPort9000,service=NameNode

    [hadoop:name=RpcActivityForPort9000,service=NameNode]
    @object=hadoop:name=RpcActivityForPort9000,service=NameNode
    getBlockLocationsNumOps=INTEGER
    getBlockLocationsAvgTime=LONG
    getBlockLocationsMinTime=LONG
    getBlockLocationsMaxTime=LONG
    rollFsImageNumOps=INTEGER
    rollFsImageAvgTime=LONG
    rollFsImageMinTime=LONG
    rollFsImageMaxTime=LONG
    ...

Using a shell redirection into a new "hbase.properties" for example or the "-f"
parameter to specify the properties name on the command line allows you to save
the new section and extend from there.

A full example is the following properties file for Hadoop and HBase hosts, named
subsquently "hadoop-hbase.properties"::

    ; Hadoop NameNode
    [hadoopFSNamesystemState]
    @object=hadoop:name=FSNamesystemState,service=NameNode
    @url=service:jmx:rmi:///jndi/rmi://${HOSTNAME1|localhost}:10001/jmxrmi
    @user=${USER|controlRole}
    @password=${PASSWORD|password}
    [hadoopNameNodeActivity]
    @object=hadoop:name=NameNodeActivity,service=NameNode
    @url=service:jmx:rmi:///jndi/rmi://${HOSTNAME1|localhost}:10001/jmxrmi
    @user=${USER|controlRole}
    @password=${PASSWORD|password}
    [hadoopRPCNameNode]
    @regexp=hadoop:name=RpcActivityForPort.*,service=NameNode
    @url=service:jmx:rmi:///jndi/rmi://${HOSTNAME1|localhost}:10001/jmxrmi
    @user=${USER|controlRole}
    @password=${PASSWORD|password}
    ; attribute=INTEGER|0:OK%3A%20%7B0%7D|2:WARN%3A%20%7B0%7D:80:<|1:FAILED%3A%20%7B0%7D:95:<
    ; *operation=FLOAT|0|2::0.1:>=|1::0.5:>

    ; Hadoop DataNode
    [hadoopFSDatasetState]
    @regexp=hadoop:name=FSDatasetState.*,service=DataNode
    @url=service:jmx:rmi:///jndi/rmi://${HOSTNAME2|localhost}:10003/jmxrmi
    @user=${USER|controlRole}
    @password=${PASSWORD|password}
    [hadoopRPCDataNode]
    @regexp=hadoop:name=RpcActivityForPort.*,service=DataNode
    @url=service:jmx:rmi:///jndi/rmi://${HOSTNAME2|localhost}:10003/jmxrmi
    @user=${USER|controlRole}
    @password=${PASSWORD|password}
    [hadoopDataNodeActivity]
    @regexp=hadoop:name=DataNodeActivity.*,service=DataNode
    @url=service:jmx:rmi:///jndi/rmi://${HOSTNAME2|localhost}:10003/jmxrmi
    @user=${USER|controlRole}
    @password=${PASSWORD|password}

    ; HBase Master
    [hbaseMasterStatistics]
    @object=hadoop:name=MasterStatistics,service=Master
    @url=service:jmx:rmi:///jndi/rmi://${HOSTNAME1|localhost}:10101/jmxrmi
    @user=${USER|controlRole}
    @password=${PASSWORD|password}
    [hbaseRPCMaster]
    @object=hadoop:name=RPCStatistics-60000,service=HBase
    @url=service:jmx:rmi:///jndi/rmi://${HOSTNAME1|localhost}:10101/jmxrmi
    @user=${USER|controlRole}
    @password=${PASSWORD|password}

    ; HBase RegionServer
    [hbaseRegionServerStatistics]
    @object=hadoop:name=RegionServerStatistics,service=RegionServer
    @url=service:jmx:rmi:///jndi/rmi://${HOSTNAME2|localhost}:10102/jmxrmi
    @user=${USER|controlRole}
    @password=${PASSWORD|password}
    [hbaseRPCRegionServer]
    @object=hadoop:name=RPCStatistics-60020,service=HBase
    @url=service:jmx:rmi:///jndi/rmi://${HOSTNAME2|localhost}:10102/jmxrmi
    @user=${USER|controlRole}
    @password=${PASSWORD|password}

    ; EOF

The above template can be used to query a master and slave node to retrieve all
current known JMX attributes and operations. This is done like so::

    $ java -DHOSTNAME1=master.foobar.com -DHOSTNAME2=slave.foobar.com -DPASSWORD=mypass \
      com.larsgeorge.jmxtoolkit.JMXToolkit -f hadoop-hbase.properties -a create -x > myjmx.properties

The ouput is saved in a new myjmx.properties file which looks like this
(shortened)::

    hadoopFSNamesystemState]
    @object=hadoop:name=FSNamesystemState,service=NameNode
    @url=service:jmx:rmi:///jndi/rmi://master.foobar.com:10001/jmxrmi
    @user=controlRole
    @password=mypass
    CapacityTotal=LONG
    CapacityUsed=LONG
    CapacityRemaining=LONG
    TotalLoad=INTEGER
    BlocksTotal=LONG
    FilesTotal=LONG
    PendingReplicationBlocks=LONG
    UnderReplicatedBlocks=LONG
    ScheduledReplicationBlocks=LONG
    FSState=STRING
    *numLiveDataNodes=INTEGER
    *numDeadDataNodes=INTEGER

    [hadoopNameNodeActivity]
    @object=hadoop:name=NameNodeActivity,service=NameNode
    @url=service:jmx:rmi:///jndi/rmi://master.foobar.com:10001/jmxrmi
    @user=controlRole
    @password=mypass
    AddBlockOps=INTEGER
    fsImageLoadTime=INTEGER
    FilesRenamed=INTEGER
    SyncsNumOps=INTEGER
    SyncsAvgTime=LONG
    SyncsMinTime=LONG
    ...

    [hadoopRPCNameNode]
    @regexp=hadoop:name=RpcActivityForPort.*,service=NameNode
    @url=service:jmx:rmi:///jndi/rmi://master.foobar.com:10001/jmxrmi
    @user=controlRole
    @password=mypass
    getBlockLocationsNumOps=INTEGER
    getBlockLocationsAvgTime=LONG
    getBlockLocationsMinTime=LONG
    getBlockLocationsMaxTime=LONG
    rollFsImageNumOps=INTEGER
    rollFsImageAvgTime=LONG
    ...

    [hbaseRegionServerStatistics]
    @object=hadoop:name=RegionServerStatistics,service=RegionServer
    @url=service:jmx:rmi:///jndi/rmi://slave.foobar.com:10102/jmxrmi
    @user=controlRole
    @password=mypass
    blockCacheFree=LONG
    memstoreSizeMB=INTEGER
    regions=INTEGER
    blockCacheCount=LONG
    blockCacheHitRatio=INTEGER
    atomicIncrementTimeNumOps=INTEGER
    atomicIncrementTimeAvgTime=LONG
    atomicIncrementTimeMinTime=LONG
    ...

Notes: JMX operations are prefixed with a "*", specific node options are
prefixed with a "@". Each section has either an @object or @regexp option to
allow for exact or matches using a regular expression. The latter is useful
when the object name changes between server restarts, which is the case for
Hadoop's DataNode for example. In such a case the JMXToolkit does scan all
object names of a host to find a matching object. The regular expression should
*not* be too broad and cover more than one object. Rather use two sections with
more specific expressions to get the wanted object name match.

With this newly created properties file the user can now query and check various
values. Once the properties file is created it can be edited to include all
required Nagios type checks. The file can also be updated exactly the same way
- the only current drawback is that comments are deleted. Checks however are
carried over, so no viable information is lost during an update. Simply run the
above create command again while specifying the existing properties file.
Example::

    $ java -DHOSTNAME1=master.foobar.com -DHOSTNAME2=slave.foobar.com -DPASSWORD=mypass \
      com.larsgeorge.jmxtoolkit.JMXToolkit -f myjmx.properties -a create

This updates the myjmx.properties in place.

Query
-----

As mentioned above, the same queries can be sent but with a lot less command
line parameters.

Example::

    $ java com.larsgeorge.jmxtoolkit.JMXToolkit -f myjmx.properties \
      -o hadoopFSNamesystemState -q CapacityRemaining

    CapacityRemaining:16583540396032

Or with an operation::

    $ java com.larsgeorge.jmxtoolkit.JMXToolkit -f myjmx.properties \
      -o hadoopFSNamesystemState -q *numLiveDataNodes

    numLiveDataNodes:20


Check
-----

In addition to what was explained above, checks can be specified on the command
line or saved in the properties file for implicit use. Using the example above,
one could edit the myjmx.properties file to include the check::

    [hadoopFSNamesystemState]
    ...
    CapacityRemaining=LONG|0|2::16583540879360:<|1::8291770439680:<
    ...

This can then be used like this::

    $ java com.larsgeorge.jmxtoolkit.JMXToolkit -f myjmx.properties \
      -o hadoopFSNamesystemState -q CapacityRemaining -a check -v

    Reading properties...
    Action -> check
    Checking value...
    Querying values...
    Details -> CapacityRemaining=LONG|0|2::16583540879360:<|1::8291770439680:<, value=16583540469760
    Check -> |0|2::16583540879360:<|1::8291770439680:<
    Exit code -> 2
    Done.

With this option all required checks can be saved with the properties file and
executed whenever needed with just a few command line details. Of course, just
as explained above, the check can include specific messages that are printed on
the console, in addition to the exit code::

    [hadoopFSNamesystemState]
    ...
    CapacityRemaining=LONG|0:OK%3A%20%7B0%2Cnumber%2C%23%7D|2:WARN%3A%20%7B0%2Cnumber%2C%23%7D:16583540879360:<|1:FAILED%3A%20%7B0%2Cnumber%2C%23%7D:8291770439680:<
    ...

And calling it returns (note the absence of the -v parameter)::

    $ java com.larsgeorge.jmxtoolkit.JMXToolkit -f myjmx.properties \
      -o hadoopFSNamesystemState -q CapacityRemaining -a check
    WARN: 16583538905088
