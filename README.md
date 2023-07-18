# DeleteVolumes

Delete AWS "available" volumes. These are orphaned when an EC2 instance stops and the associated EBS volume was not set to "delete on terminate."

This program can send Slack messages - it depends on another small library - `github.com/rcprcp/SimpleSlack` - feel free to remove that code if it does not work for your use case.

Currently tested with Java17. 

This program uses an environment variable containing the names of the people that will recceive the Slack messages: 

```export SLACK_NOTIFICATION_LIST="bobp;Mickey;Minnie123```

## Download 
```shell
git clone https://github.com/rcprcp/DeleteVolumes
```
### Build
cd into the DeleteVolumes directory then: 

```shell
mvn clean package 
```

### Run 

```shell
java -jar target/DeleteVolumes-1.0-SNAPSHOT-jar-with-dependencies.jar 
```
