package com.cottagecoders;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
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


public class DeleteVolumes {
  private static final Logger LOG = LogManager.getLogger(DeleteVolumes.class);

  @Parameter(names = "--dryRun", description = "Dry run - don't make changes; don't delete anything")
  private static boolean dryRun = false;

  @Parameter(names = {"--help"}, description = "Help")
  private static boolean help = false;

  public static void main(String[] args) {
    DeleteVolumes dv = new DeleteVolumes();
    JCommander jc = JCommander.newBuilder().addObject(dv).build();
    jc.parse(args);

    if (help) {
      jc.usage();
      return;
    }

    dv.iterateEc2Instances();

  }

  private void iterateEc2Instances() {
    int gb = 0;
    try (Ec2Client client = Ec2Client.builder().credentialsProvider(ProfileCredentialsProvider.create()).region(Region.US_EAST_2).build()) {

      for (software.amazon.awssdk.services.ec2.model.Region region : client.describeRegions().regions()) {
        Region r = Region.of(region.regionName());
        try (Ec2Client ec2 = Ec2Client.builder().credentialsProvider(ProfileCredentialsProvider.create()).region(r).build()) {

          DescribeInstancesRequest request = DescribeInstancesRequest.builder().build();
          DescribeInstancesResponse instancesResponse = ec2.describeInstances(request);

          LOG.info("Region: {}", region.regionName());
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
    LOG.info("{} gb. approximate cost {} ", gb, gb * 0.08d);

  }
}
