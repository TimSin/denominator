package denominator.ultradns;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.inject.Inject;

import denominator.common.PeekingIterator;
import denominator.model.ResourceRecordSet;
import denominator.model.ResourceRecordSet.Builder;
import denominator.model.profile.Geo;
import denominator.ultradns.model.DirectionalRecord;
import denominator.ultradns.model.DirectionalGroup;

import static denominator.common.Util.peekingIterator;
import static denominator.common.Util.toMap;

/**
 * Generally, this iterator will produce {@link ResourceRecordSet} for only a single record type.
 * However, there are special cases where this can produce multiple. For example, {@link
 * DirectionalPool.RecordType#IPV4} and {@link DirectionalPool.RecordType#IPV6} emit both address
 * ({@code A} or {@code AAAA}) and {@code CNAME} records.
 */
class GroupGeoRecordByNameTypeCustomIterator implements Iterator<ResourceRecordSet<?>> {

  private final Map<String, Geo> cache = new LinkedHashMap<String, Geo>();
  private final UltraDNSRest api;
  private final PeekingIterator<DirectionalRecord> peekingIterator;

  private GroupGeoRecordByNameTypeCustomIterator(UltraDNSRest api,
                                                 Iterator<DirectionalRecord> sortedIterator) {
    this.api = api;
    this.peekingIterator = peekingIterator(sortedIterator);
  }

  static boolean typeTTLAndGeoGroupEquals(DirectionalRecord actual, DirectionalRecord expected) {
    return actual.getType().equals(expected.getType()) && actual.ttl == expected.ttl
           && actual.getGeoGroupId().equals(expected.getGeoGroupId());
  }

  /**
   * skips no response records as they aren't portable
   */
  @Override
  public boolean hasNext() {
    if (!peekingIterator.hasNext()) {
      return false;
    }
    DirectionalRecord record = peekingIterator.peek();
    if (record.isNoResponseRecord()) {
      // TODO: log as this is unsupported
      peekingIterator.next();
    }
    return true;
  }

  @Override
  public ResourceRecordSet<?> next() {
    DirectionalRecord record = peekingIterator.next();

    Builder<Map<String, Object>>
        builder =
        ResourceRecordSet.builder().name(record.name).type(record.getType())
            .qualifier(record.getGeoGroupName()).ttl(record.ttl);

    builder.add(toMap(record.getType(), record.rdata));

    if (!cache.containsKey(record.getGeoGroupId())) {
      /*Geo profile = Geo.create(api.getDirectionalDNSGroupDetails(record.getGeoGroupId()).getRegionToTerritories());
      cache.put(record.getGeoGroupId(), profile);*/
    }

    builder.geo(cache.get(record.getGeoGroupId()));
    while (hasNext()) {
      DirectionalRecord next = peekingIterator.peek();
      if (typeTTLAndGeoGroupEquals(next, record)) {
        peekingIterator.next();
        builder.add(toMap(record.getType(), next.rdata));
      } else {
        break;
      }
    }
    return builder.build();
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

  static final class Factory {

    private final UltraDNSRest api;

    @Inject
    Factory(UltraDNSRest api) {
      this.api = api;
    }

    /**
     * @param sortedIterator only contains records with the same {@link DirectionalRecord#name()},
     *                       sorted by {@link DirectionalRecord#type()}, {@link
     *                       DirectionalRecord#getGeolocationGroup()} or {@link
     *                       DirectionalRecord#group()}
     */
    Iterator<ResourceRecordSet<?>> create(Iterator<DirectionalRecord> sortedIterator) {
      return new GroupGeoRecordByNameTypeCustomIterator(api, sortedIterator);
    }
  }
}
