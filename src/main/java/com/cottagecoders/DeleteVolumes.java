package com.cottagecoders;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.cottagecoders.simpleslack.FetchMembers;
import com.cottagecoders.simpleslack.SendSlackMessage;
import com.cottagecoders.simpleslack.userlist.Member;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DeleteVolumeRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeVolumesResponse;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.ec2.model.Volume;

import java.io.IOException;
import java.util.Date;
import java.util.Map;


public class DeleteVolumes {
  private static final Logger LOG = LogManager.getLogger(DeleteVolumes.class);

  @Parameter(names = "--dryRun", description = "Dry run - don't make changes; don't delete anything")
  private static boolean dryRun = false;

  public static void main(String[] args) {
    DeleteVolumes dv = new DeleteVolumes();
    JCommander jc = JCommander.newBuilder().addObject(dv).build();
    jc.parse(args);

    dv.iterateEc2Instances();

  }

  private void iterateEc2Instances() {
    StringBuilder sb = new StringBuilder(2000);
    String msg = String.format("DeleteVolumes: %s\n", new Date());
    sb.append(msg);

    int gb = 0;
    try (Ec2Client client = Ec2Client.builder().credentialsProvider(ProfileCredentialsProvider.create()).region(Region.US_EAST_2).build()) {

      for (software.amazon.awssdk.services.ec2.model.Region region : client.describeRegions().regions()) {
        Region r = Region.of(region.regionName());
        try (Ec2Client ec2 = Ec2Client.builder().credentialsProvider(ProfileCredentialsProvider.create()).region(r).build()) {

          DescribeInstancesRequest request = DescribeInstancesRequest.builder().build();
          DescribeInstancesResponse instancesResponse = ec2.describeInstances(request);

          LOG.info(String.format("Region: %s", region.regionName()));

          for (Reservation reservation : instancesResponse.reservations()) {
            for (Instance instance : reservation.instances()) {
              DescribeVolumesResponse resp;
              try {
                resp = ec2.describeVolumes();

              } catch (Ec2Exception | SdkClientException ex) {
                continue;
              }
              for (Volume v : resp.volumes()) {
                if (v.state().name().equalsIgnoreCase("available")) {
                  gb += v.size();
                  if (!dryRun) {
                    ec2.deleteVolume(DeleteVolumeRequest.builder().volumeId(v.volumeId()).build());
                  }
                }
              }
            }
          }
        }
      }
    }

    // make this message look nice.
    if (gb == 0) {
      msg = "0 gb of detached volumes!  :smile:";
    } else {
      msg = String.format("%d gb. of detached volumes! approximate savings  :dollar-spin: %.2f  \n", gb, gb * 0.08d);
    }

    LOG.info(msg);
    sb.append(msg);
    slackIt(sb.toString());

  }

  void slackIt(String msg) {

    // fetch and parse the notification list.
    String envVar = System.getenv("SLACK_NOTIFICATION_LIST");
    if (StringUtils.isEmpty(envVar)) {
      System.out.println("No notifications - SLACK_NOTIFICATION_LIST is empty.");
      return;
    }

    String[] slackDisplayNames = envVar.split(",");
    if (slackDisplayNames.length == 0) {
      System.out.println("No notifications - can't split SLACK_NOTIFICATION_LIST.");
      return;
    }

    try {
      FetchMembers fetch = new FetchMembers();

      Map<String, Member> members = null;
      // gather list of members from Slack
      members = fetch.fetchAllMembers();


      for (String s : slackDisplayNames) {
        // get specific user
        Member member = members.get(s.trim());
        if (member == null) {
          // user lookup failed.
          continue;
        }
        // TODO: delete debugging code.
        if (member.getName().contains("bobp") || member.getName().contains("lark")) {
          System.out.println("stop. name matched");
        }

        SendSlackMessage ssm = new SendSlackMessage();
        ssm.sendDM(member.getId(), msg);
      }

    } catch (IOException | InterruptedException ex) {
      System.out.println("fetch.fetchAllMembers() exception: " + ex.getMessage());
      ex.printStackTrace();
      System.exit(12);
    }
  }
}
