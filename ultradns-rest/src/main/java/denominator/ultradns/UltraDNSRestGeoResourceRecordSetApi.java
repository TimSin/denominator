package denominator.ultradns;

import java.sql.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.Lazy;
import denominator.Provider;
import denominator.common.Filter;
import denominator.model.ResourceRecordSet;
import denominator.profile.GeoResourceRecordSetApi;
import denominator.ultradns.model.RRSet;
import denominator.ultradns.model.Record;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import static denominator.ResourceTypeToValue.lookup;
import static denominator.common.Preconditions.checkArgument;
import static denominator.common.Preconditions.checkNotNull;
import static denominator.common.Util.concat;
import static denominator.common.Util.filter;
import static denominator.common.Util.nextOrNull;
import static denominator.common.Util.toMap;
import static denominator.model.ResourceRecordSets.nameAndTypeEqualTo;

import denominator.ultradns.model.DirectionalRecord;
import denominator.ultradns.model.DirectionalGroup;

final class UltraDNSRestGeoResourceRecordSetApi implements GeoResourceRecordSetApi {
  private static final Logger logger = Logger.getLogger(UltraDNSRestGeoResourceRecordSetApi.class);
  private static final Filter<ResourceRecordSet<?>> IS_GEO = new Filter<ResourceRecordSet<?>>() {
    @Override
    public boolean apply(ResourceRecordSet<?> in) {
      return in != null && in.geo() != null;
    }
  };
  private static final int DEFAULT_TTL = 300;

  private final Collection<String> supportedTypes;
  private final Lazy<Map<String, Collection<String>>> regions;
  private final UltraDNSRest api;
  private final GroupGeoRecordByNameTypeCustomIterator.Factory iteratorFactory;
  private final String zoneName;
  private final Filter<DirectionalRecord> isCNAME = new Filter<DirectionalRecord>() {
    @Override
    public boolean apply(DirectionalRecord input) {
      return "CNAME".equals(input.getType());
    }
  };

  UltraDNSRestGeoResourceRecordSetApi(Collection<String> supportedTypes,
                                      Lazy<Map<String, Collection<String>>> regions,
                                      UltraDNSRest api,
                                      GroupGeoRecordByNameTypeCustomIterator.Factory iteratorFactory,
                                      String zoneName) {
    this.supportedTypes = supportedTypes;
    this.regions = regions;
    this.api = api;
    this.iteratorFactory = iteratorFactory;
    this.zoneName = zoneName;
  }

  @Override
  public Map<String, Collection<String>> supportedRegions() {
    return regions.get();
  }

  @Override
  public Iterator<ResourceRecordSet<?>> iterator() {
    List<Iterable<ResourceRecordSet<?>>> eachPool = new ArrayList<Iterable<ResourceRecordSet<?>>>();
    final Map<String, Integer> nameAndType = api.getDirectionalPoolsOfZone(zoneName).getNameAndType();
    for (final String poolName : nameAndType.keySet()) {
      eachPool.add(new Iterable<ResourceRecordSet<?>>() {
        public Iterator<ResourceRecordSet<?>> iterator() {
          return iteratorForDNameAndDirectionalType(poolName, nameAndType.get(poolName));
        }
      });
    }
    return concat(eachPool);
  }

  @Override
  public Iterator<ResourceRecordSet<?>> iterateByName(String name) {
    return iteratorForDNameAndDirectionalType(checkNotNull(name, "description"), 0);
  }

  @Override
  public Iterator<ResourceRecordSet<?>> iterateByNameAndType(String name, String type) {
    checkNotNull(name, "description");
    checkNotNull(type, "type");
    Filter<ResourceRecordSet<?>> filter = nameAndTypeEqualTo(name, type);
    if (!supportedTypes.contains(type)) {
      return Collections.<ResourceRecordSet<?>>emptyList().iterator();
    }
    if ("CNAME".equals(type)) {
      // retain original type (this will filter out A, AAAA)
      return filter(
          concat(iteratorForDNameAndDirectionalType(name, lookup("A")),
                 iteratorForDNameAndDirectionalType(name, lookup("AAAA"))), filter);
    } else if ("A".equals(type) || "AAAA".equals(type)) {
      int dirType = "AAAA".equals(type) ? lookup("AAAA") : lookup("A");
      Iterator<ResourceRecordSet<?>> iterator = iteratorForDNameAndDirectionalType(name, dirType);
      // retain original type (this will filter out CNAMEs)
      return filter(iterator, filter);
    } else {
      return iteratorForDNameAndDirectionalType(name, dirType(type));
    }
  }

  @Override
  public ResourceRecordSet<?> getByNameTypeAndQualifier(String name, String type,
                                                        String qualifier) {
    checkNotNull(name, "description");
    checkNotNull(type, "type");
    checkNotNull(qualifier, "qualifier");
    if (!supportedTypes.contains(type)) {
      return null;
    }
    Iterator<DirectionalRecord> records = recordsByNameTypeAndQualifier(name, type, qualifier);
    return nextOrNull(iteratorFactory.create(records));
  }

  private Iterator<DirectionalRecord> recordsByNameTypeAndQualifier(String name, String type,
                                                                                 String qualifier) {
    if ("CNAME".equals(type)) {
      return filter(
          concat(recordsForNameTypeAndQualifier(name, "A", qualifier),
                 recordsForNameTypeAndQualifier(name, "AAAA", qualifier)), isCNAME);
    } else {
      return recordsForNameTypeAndQualifier(name, type, qualifier);
    }
  }

  private Iterator<DirectionalRecord> recordsForNameTypeAndQualifier(String name, String type,
                                                                                  String qualifier) {
    try {
      return api.getDirectionalDNSRecordsForHost(zoneName, name, dirType(type))
              .getDirectionalRecordsByGroup(qualifier).iterator();
    } catch (UltraDNSRestException e) {
      switch (e.code()) {
        case UltraDNSRestException.GROUP_NOT_FOUND:
        case UltraDNSRestException.DIRECTIONALPOOL_NOT_FOUND:
          return Collections.<DirectionalRecord>emptyList().iterator();
      }
      throw e;
    }
  }

  @Override
  public void put(ResourceRecordSet<?> rrset) {
    checkNotNull(rrset, "rrset was null");
    checkArgument(rrset.qualifier() != null, "no qualifier on: %s", rrset);
    checkArgument(IS_GEO.apply(rrset), "%s failed on: %s", IS_GEO, rrset);
    checkArgument(supportedTypes.contains(rrset.type()), "%s not a supported type for geo: %s",
                  rrset.type(),
                  supportedTypes);
    int ttlToApply = rrset.ttl() != null ? rrset.ttl() : DEFAULT_TTL;
    String group = rrset.qualifier();
    Map<String, Collection<String>> regions = rrset.geo().regions();
    DirectionalGroup directionalGroup = new DirectionalGroup();
    directionalGroup.setName(group);
    directionalGroup.setRegionToTerritories(regions);
    List<Map<String, Object>>
        recordsLeftToCreate =
        new ArrayList<Map<String, Object>>(rrset.records());
    Iterator<DirectionalRecord>
        iterator =
        recordsByNameTypeAndQualifier(rrset.name(), rrset.type(), group);
    while (iterator.hasNext()) {
      DirectionalRecord record = iterator.next();
      Map<String, Object> rdata = toMap(record.getType(), record.rdata);
      if (recordsLeftToCreate.contains(rdata)) {
        recordsLeftToCreate.remove(rdata);
        boolean shouldUpdate = false;
        if (ttlToApply != record.ttl) {
          record.ttl = ttlToApply;
          shouldUpdate = true;
        } else {
          directionalGroup = new DirectionalGroup();
          //directionalGroup = api.getDirectionalDNSGroupDetails(record.getGeoGroupId());
          if (!regions.equals(directionalGroup.getRegionToTerritories())) {
            directionalGroup.setRegionToTerritories(regions);
            shouldUpdate = true;
          }
        }
        if (shouldUpdate) {
          try {
            api.updateDirectionalPoolRecord(record, directionalGroup);
          } catch (UltraDNSRestException e) {
            // lost race
            if (e.code() != UltraDNSRestException.RESOURCE_RECORD_ALREADY_EXISTS) {
              throw e;
            }
          }
        }
      } else {
        int indexToDelete = -1;
        String rData = "";
        int intType = lookup(record.getType());

        if (record.getRdata() != null && !record.getRdata().isEmpty()) {
          rData = StringUtils.join(record.getRdata(), " ");
        }

        List<RRSet> rrSets = api.getResourceRecordsOfDNameByType(zoneName, record.getName(),
                intType).getRrSets();
        if (rrSets != null && !rrSets.isEmpty()) {
          RRSet rrSet = rrSets.get(0);
          if (rrSet != null & rrSet.getRdata() != null) {
            indexToDelete = rrSet.getRdata().indexOf(rData);
          }
        }
        if (indexToDelete >= 0 ) {
          try {
            api.deleteResourceRecord(zoneName, intType, record.getName(), indexToDelete);
          } catch (UltraDNSRestException e) {
              throw e;
          }
        }
      }
    }

    if (!recordsLeftToCreate.isEmpty()) {
      // shotgun create
      String poolId;
      try {
        String type = rrset.type();
        if ("CNAME".equals(type)) {
          type = "A";
        }
        // Hvv to work
        poolId = "";
        api.addDirectionalPool(zoneName, rrset.name(), type);
      } catch (UltraDNSRestException e) {
        // lost race
        if (e.code() == UltraDNSRestException.POOL_ALREADY_EXISTS) {
          poolId = "";
          //poolId = api.getDirectionalPoolsOfZone(zoneName).get(rrset.name()); Have to work on this
        } else {
          throw e;
        }
      }
      DirectionalRecord record = new DirectionalRecord();
      record.setType(rrset.type());
      record.setTtl(ttlToApply);

      for (Map<String, Object> rdata : recordsLeftToCreate) {
        for (Object rdatum : rdata.values()) {
          record.rdata.add(rdatum.toString());
        }
        try {
          api.addDirectionalPoolRecord(record, directionalGroup, poolId);
        } catch (UltraDNSRestException e) {
          // lost race
          if (e.code() != UltraDNSRestException.POOL_RECORD_ALREADY_EXISTS) {
            throw e;
          }
        }
      }
    }
  }

  private int dirType(String type) {
    if ("A".equals(type) || "CNAME".equals(type)) {
      return lookup("A");
    } else if ("AAAA".equals(type)) {
      return lookup("AAAA");
    } else {
      return lookup(type);
    }
  }

  @Override
  public void deleteByNameTypeAndQualifier(String name, String type, String qualifier) {
    Iterator<DirectionalRecord> record = recordsByNameTypeAndQualifier(name, type, qualifier);
    while (record.hasNext()) {
      try {
        api.deleteDirectionalPoolRecord(record.next().id);
      } catch (UltraDNSRestException e) {
        // lost race
        if (e.code() != UltraDNSRestException.DIRECTIONALPOOL_RECORD_NOT_FOUND) {
          throw e;
        }
      }
    }
  }

  private Iterator<ResourceRecordSet<?>> iteratorForDNameAndDirectionalType(String name,
                                                                            int dirType) {
    List<DirectionalRecord> list;
    try {
      list = api.getDirectionalDNSRecordsForHost(zoneName, name, dirType).buildDirectionalRecords();
    } catch (UltraDNSRestException e) {
      if (e.code() == UltraDNSRestException.DIRECTIONALPOOL_NOT_FOUND) {
        list = Collections.emptyList();
      } else {
        throw e;
      }
    }
    return iteratorFactory.create(list.iterator());
  }

  static final class Factory implements GeoResourceRecordSetApi.Factory {

    private final Collection<String> supportedTypes;
    private final Lazy<Map<String, Collection<String>>> regions;
    private final UltraDNSRest api;
    private final GroupGeoRecordByNameTypeCustomIterator.Factory iteratorFactory;

    @Inject
    Factory(Provider provider, @Named("geo") Lazy<Map<String, Collection<String>>> regions,
            UltraDNSRest api,
            GroupGeoRecordByNameTypeCustomIterator.Factory iteratorFactory) {
      this.supportedTypes = provider.profileToRecordTypes().get("geo");
      this.regions = regions;
      this.api = api;
      this.iteratorFactory = iteratorFactory;
    }

    @Override
    public GeoResourceRecordSetApi create(String name) {
      checkNotNull(name, "name was null");
      // Eager fetch of regions to determine if directional records are supported or not.
      try {
        regions.get();
      } catch (UltraDNSRestException e) {
        if (e.code() == UltraDNSRestException.DIRECTIONAL_NOT_ENABLED) {
          return null;
        }
        throw e;
      }
      return new UltraDNSRestGeoResourceRecordSetApi(supportedTypes, regions, api, iteratorFactory,
                                                 name);
    }
  }
}