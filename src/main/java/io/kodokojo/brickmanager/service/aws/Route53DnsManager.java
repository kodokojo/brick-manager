/**
 * Kodo Kojo - API frontend which dispatch REST event to Http services or publish event on EvetnBus.
 * Copyright © 2016 Kodo Kojo (infos@kodokojo.io)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package io.kodokojo.brickmanager.service.aws;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.route53.AmazonRoute53Client;
import com.amazonaws.services.route53.model.*;
import io.kodokojo.commons.service.dns.DnsEntry;
import io.kodokojo.commons.service.dns.DnsManager;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isNotBlank;

public class Route53DnsManager implements DnsManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(Route53DnsManager.class);

    private final String domainName;

    private final Region region;

    public Route53DnsManager(String domainName, Region region) {
        if (isBlank(domainName)) {
            throw new IllegalArgumentException("domainName must be defined.");
        }
        this.domainName = domainName.endsWith(".") ? domainName : domainName + ".";
        this.region = region;
    }

    @Override
    public boolean createOrUpdateDnsEntry(DnsEntry dnsEntry) {
        if (dnsEntry == null) {
            throw new IllegalArgumentException("dnsEntry must be defined.");
        }
        createOrUpdateDnsEntries(Collections.singleton(dnsEntry));
        return true;
    }

    @Override
    public void createOrUpdateDnsEntries(Set<DnsEntry> dnsEntries) {
        if (dnsEntries == null) {
            throw new IllegalArgumentException("dnsEntries must be defined.");
        }

        HostedZone hostedZone = getHostedZone();
        List<Change> changes = new ArrayList<>();
        if (hostedZone != null) {
            for (DnsEntry dnsEntry : dnsEntries) {
                if (!containEntry(dnsEntry, true)) {
                    List<ResourceRecord> resourceRecords = new ArrayList<>();

                    ResourceRecord resourceRecord = new ResourceRecord();
                    String value = dnsEntry.getValue();
                    resourceRecord.setValue((dnsEntry.getType().equals(DnsEntry.Type.CNAME) ? valideDnsName(value) : value));
                    resourceRecords.add(resourceRecord);

                    ResourceRecordSet resourceRecordSet = new ResourceRecordSet();
                    resourceRecordSet.setName(valideDnsName(dnsEntry.getName()));
                    resourceRecordSet.setType(RRType.valueOf(dnsEntry.getType().toString()));
                    resourceRecordSet.setTTL(300L);

                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Adding following record to route53 {} with become {}", dnsEntry, resourceRecordSet.toString(), resourceRecord.toString());
                    }

                    resourceRecordSet.setResourceRecords(resourceRecords);

                    Change change = new Change();
                    change.setAction(dnsEntryExist(dnsEntry) ? ChangeAction.UPSERT : ChangeAction.CREATE);
                    change.setResourceRecordSet(resourceRecordSet);
                    changes.add(change);
                }
            }
            if (CollectionUtils.isNotEmpty(changes)) {
                ChangeResourceRecordSetsRequest request = new ChangeResourceRecordSetsRequest();
                ChangeBatch changeBatch = new ChangeBatch();
                changeBatch.setChanges(changes);
                request.setChangeBatch(changeBatch);
                request.setHostedZoneId(getHostedZoneID(hostedZone));
                //ChangeResourceRecordSetsResult result =
                try {
                    AmazonRoute53Client client = createClient();
                    client.changeResourceRecordSets(request);
                } catch (PriorRequestNotCompleteException e) {
                    LOGGER.error("Unable to create or update following entry in Route53 {}.", StringUtils.join(dnsEntries, ","));
                }
            }
        }

    }

    @Override
    public boolean dnsEntryExist(DnsEntry dnsEntry) {
        return containEntry(dnsEntry, false);
    }

    private boolean containEntry(DnsEntry dnsEntry, boolean stric) {
        if (dnsEntry == null) {
            throw new IllegalArgumentException("dnsEntry must be defined.");
        }
        HostedZone hostedZone = getHostedZone();
        boolean found = false;
        if (hostedZone != null) {
            Iterator<ResourceRecordSet> iterator = getResourceRecordSet(hostedZone).iterator();
            String dnsEntryName = valideDnsName(dnsEntry.getName());
            while (!found && iterator.hasNext()) {
                ResourceRecordSet recordSet = iterator.next();
                found = recordSet.getName().equals(dnsEntryName)
                        && DnsEntry.Type.valueOf(recordSet.getType()).equals(dnsEntry.getType());
                if (found && stric) {
                    Iterator<ResourceRecord> recordIterator = recordSet.getResourceRecords().iterator();
                    boolean sameValue = false;
                    while (!sameValue && recordIterator.hasNext()) {
                        ResourceRecord record = recordIterator.next();
                        sameValue = dnsEntry.getValue().equals(record.getValue());
                    }
                    found = sameValue;
                }
            }
        }
        return found;
    }

    @Override
    public List<DnsEntry> getDnsEntries(String name) {
        String dnsName = null;
        if (isNotBlank(name)) {
            dnsName = valideDnsName(name);
        }
        HostedZone hostedZone = getHostedZone();
        List<DnsEntry> res = new ArrayList<>();
        if (hostedZone != null) {
            List<ResourceRecordSet> resourceRecordSet = getResourceRecordSet(hostedZone);
            for (ResourceRecordSet recordSet : resourceRecordSet) {
                if ((isNotBlank(dnsName) && recordSet.getName().equals(dnsName)) || isBlank(dnsName)) {
                    DnsEntry dnsEntry = convertDnsEnrty(recordSet);
                    res.add(dnsEntry);
                }
            }
        }
        return res;
    }


    private AmazonRoute53Client createClient() {
        AWSCredentials credentials = new DefaultAWSCredentialsProviderChain().getCredentials();
        AmazonRoute53Client client = new AmazonRoute53Client(credentials);
        client.setRegion(region == null ? Region.getRegion(Regions.EU_WEST_1) : region);
        return client;
    }


    private HostedZone getHostedZone() {
        AmazonRoute53Client client = createClient();
        ListHostedZonesByNameRequest listHostedZonesByNameRequest = new ListHostedZonesByNameRequest();
        listHostedZonesByNameRequest.setDNSName(domainName);
        ListHostedZonesByNameResult result = client.listHostedZonesByName(listHostedZonesByNameRequest);

        Iterator<HostedZone> iterator = result.getHostedZones().iterator();
        HostedZone hostedZone = null;
        while (hostedZone == null && iterator.hasNext()) {
            HostedZone currentZone = iterator.next();
            hostedZone = currentZone.getName().equals(domainName) ? currentZone : null;
        }
        return hostedZone;
    }

    private List<ResourceRecordSet> getResourceRecordSet(HostedZone hostedZone) {
        AmazonRoute53Client client = createClient();
        ListResourceRecordSetsRequest listResourceRecordSetsRequest = new ListResourceRecordSetsRequest();
        listResourceRecordSetsRequest.setHostedZoneId(getHostedZoneID(hostedZone));
        ListResourceRecordSetsResult recordSetsResult = client.listResourceRecordSets(listResourceRecordSetsRequest);
        return recordSetsResult.getResourceRecordSets();
    }

    private static String valideDnsName(String name) {
        return name.endsWith(".") ? name : name + ".";
    }

    private static String getHostedZoneID(HostedZone hostedZone) {
        return hostedZone.getId().substring("/hostedzone/".length());
    }


    private static DnsEntry convertDnsEnrty(ResourceRecordSet resourceRecordSet) {
        String dnsName = resourceRecordSet.getName();
        DnsEntry.Type dnsType = DnsEntry.Type.valueOf(resourceRecordSet.getType());
        String value = resourceRecordSet.getResourceRecords().get(0).getValue();
        return new DnsEntry(dnsName, dnsType, value);
    }
}


