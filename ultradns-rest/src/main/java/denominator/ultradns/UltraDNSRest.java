package denominator.ultradns;

import denominator.ultradns.model.*;

import feign.Body;
import feign.Headers;
import feign.Param;
import feign.RequestLine;

import java.util.*;

@Headers("Content-Type: application/json")
interface UltraDNSRest {

  @RequestLine("GET /status")
  Status getNeustarNetworkStatus();

  @RequestLine("GET /accounts")
  AccountList getAccountsListOfUser();

  @RequestLine("GET /accounts/{accountName}/zones")
  ZoneList getZonesOfAccount(@Param("accountName") String accountName);

  @RequestLine("POST /zones")
  @Body("%7B\"properties\": %7B\"name\": \"{name}\",\"accountName\": \"{accountName}\",\"type\": \"{type}\"%7D, " +
          "\"primaryCreateInfo\": %7B\"forceImport\": {forceImport},\"createType\": \"{createType}\"%7D%7D")
  void createPrimaryZone(@Param("name") final String name, @Param("accountName") final String accountName,
                         @Param("type") final String type, @Param("forceImport") final boolean forceImport,
                         @Param("createType") final String createType);

  /**
   * @throws UltraDNSRestException with code {@link UltraDNSRestException#ZONE_NOT_FOUND}.
   */
  @RequestLine("DELETE /zones/{zoneName}")
  void deleteZone(@Param("zoneName") final String zoneName);

  @RequestLine("GET /zones/{zoneName}/rrsets")
  RRSetList getResourceRecordsOfZone(@Param("zoneName") String zoneName);

  @RequestLine("GET /zones/{zoneName}/rrsets/{rrType}/{hostName}")
  RRSetList getResourceRecordsOfDNameByType(@Param("zoneName") String zoneName,
                                            @Param("hostName") String hostName,
                                            @Param("rrType") int rrType);

  @RequestLine("POST /zones/{zoneName}/rrsets/{rrType}/{hostName}")
  void createResourceRecord(@Param("zoneName") String zoneName,
                            @Param("rrType") int rrType,
                            @Param("hostName") String hostName,
                            RRSet rrSet);

  @RequestLine("PUT /zones/{zoneName}/rrsets/{rrType}/{hostName}")
  void updateResourceRecord(@Param("zoneName") String zoneName,
                            @Param("rrType") int rrType,
                            @Param("hostName") String hostName,
                            RRSet rrSet);

  @RequestLine("PATCH /zones/{zoneName}/rrsets/{rrType}/{hostName}")
  void partialUpdateResourceRecord(@Param("zoneName") String zoneName,
                            @Param("rrType") int rrType,
                            @Param("hostName") String hostName,
                            RRSet rrSet);

  @RequestLine("DELETE /zones/{zoneName}/rrsets/{rrType}/{hostName}")
  Status deleteResourceRecordByNameType(@Param("zoneName") String zoneName,
                                      @Param("rrType") int rrType,
                                      @Param("hostName") String hostName);

  @RequestLine("PATCH /zones/{zoneName}/rrsets/{rrType}/{hostName}")
  @Headers("Content-Type: application/json-patch+json")
  @Body("%5B%7B\"op\": \"remove\",\"path\": \"/rdata/{index}\"%7D%5D")
  Status deleteResourceRecord(@Param("zoneName") String zoneName,
                              @Param("rrType") int rrType,
                              @Param("hostName") String hostName,
                              @Param("index") int index);

  @RequestLine("POST")
  @Body("<v01:getLoadBalancingPoolsByZone><zoneName>{zoneName}</zoneName><lbPoolType>RR</lbPoolType></v01:getLoadBalancingPoolsByZone>")
  Map<NameAndType, String> getLoadBalancingPoolsByZone(@Param("zoneName") String zoneName);

  @RequestLine("POST")
  @Body("<v01:getRRPoolRecords><lbPoolId>{poolId}</lbPoolId></v01:getRRPoolRecords>")
  List<Record> getRRPoolRecords(@Param("poolId") String poolId);

  @RequestLine("POST")
  @Body("<v01:addRRLBPool><transactionID /><zoneName>{zoneName}</zoneName><hostName>{hostName}</hostName><description>{poolRecordType}</description><poolRecordType>{poolRecordType}</poolRecordType><rrGUID /></v01:addRRLBPool>")
  String addRRLBPool(@Param("zoneName") String zoneName, @Param("hostName") String name,
                     @Param("poolRecordType") int typeCode);

  @RequestLine("POST")
  @Body("<v01:addRecordToRRPool><transactionID /><roundRobinRecord lbPoolID=\"{lbPoolID}\" info1Value=\"{address}\" ZoneName=\"{zoneName}\" Type=\"{type}\" TTL=\"{ttl}\"/></v01:addRecordToRRPool>")
  void addRecordToRRPool(@Param("type") int type, @Param("ttl") int ttl,
                         @Param("address") String rdata,
                         @Param("lbPoolID") String lbPoolID, @Param("zoneName") String zoneName);

  @RequestLine("POST")
  @Body("<v01:updateRecordOfRRPool><transactionID /><resourceRecord rrGuid=\"{rrGuid}\" lbPoolID=\"{lbPoolID}\" info1Value=\"{info1Value}\" TTL=\"{ttl}\"/></v01:updateRecordOfRRPool>")
  void updateRecordOfRRPool(@Param("rrGuid") String rrGuid, @Param("lbPoolID") String lbPoolID,
                            @Param("info1Value") String info1Value, @Param("ttl") int ttl);

  /**
   * @throws UltraDNSRestException with code {@link UltraDNSRestException#POOL_NOT_FOUND} and {@link
   *                           UltraDNSRestException#RESOURCE_RECORD_NOT_FOUND}.
   */
  @RequestLine("POST")
  @Body("<v01:deleteLBPool><transactionID /><lbPoolID>{lbPoolID}</lbPoolID><DeleteAll>Yes</DeleteAll><retainRecordId /></v01:deleteLBPool>")
  void deleteLBPool(@Param("lbPoolID") String id);

  @RequestLine("POST")
  @Body("<v01:getAvailableRegions />")
  Map<String, Collection<String>> getAvailableRegions();

  /**
   * This is kept in hold for migration
   * Commenting & stubbing the method
   */
  @RequestLine("POST")
  @Body("<v01:getDirectionalDNSGroupDetails><GroupId>{GroupId}</GroupId></v01:getDirectionalDNSGroupDetails>")
  DirectionalGroup getDirectionalDNSGroupDetails(@Param("GroupId") String groupId);

  /**
   * @throws UltraDNSRestException with code {@link UltraDNSRestException#POOL_RECORD_ALREADY_EXISTS}.
   */
  @RequestLine("POST")
  String addDirectionalPoolRecord(@Param("record") DirectionalRecord toCreate,
                                  @Param("group") DirectionalGroup group,
                                  @Param("poolId") String poolId);

  /**
   * @throws UltraDNSRestException with code {@link UltraDNSRestException#RESOURCE_RECORD_ALREADY_EXISTS}.
   */
  @RequestLine("POST")
  void updateDirectionalPoolRecord(@Param("record") DirectionalRecord update,
                                   @Param("group") DirectionalGroup group);

  @RequestLine("GET /zones/{zoneName}/rrsets/?q=kind:DIR_POOLS")
  RRSetList getDirectionalPoolsOfZone(@Param("zoneName") String zoneName);

  @RequestLine("GET /zones/{zoneName}/rrsets/{poolRecordType}/{hostName}?q=kind:DIR_POOLS")
  RRSetList getDirectionalDNSRecordsForHost(@Param("zoneName") String zoneName,
                                            @Param("hostName") String name,
                                            @Param("poolRecordType") int rrType);

  @RequestLine("POST /zones/{zoneName}/rrsets/{poolRecordType}/{hostName}")
  @Body("%7B\"profile\": %7B\"@context\": \"http://schemas.ultradns.com/DirPool.jsonschema\",\"description\": \"{poolRecordType}\"%7D%7D")
  Status addDirectionalPool(@Param("zoneName") String zoneName, @Param("hostName") String name,
                            @Param("poolRecordType") String type);

  @RequestLine("POST")
  @Body("<v01:deleteDirectionalPoolRecord><transactionID /><dirPoolRecordId>{dirPoolRecordId}</dirPoolRecordId></v01:deleteDirectionalPoolRecord>")
  void deleteDirectionalPoolRecord(@Param("dirPoolRecordId") String id);

  @RequestLine("POST")
  @Body("<v01:deleteDirectionalPool><transactionID /><dirPoolID>{dirPoolID}</dirPoolID><retainRecordID /></v01:deleteDirectionalPool>")
  void deleteDirectionalPool(@Param("dirPoolID") String dirPoolID);

  class NameAndType {

    String name;
    String type;

    @Override
    public int hashCode() {
      return 37 * name.hashCode() + type.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || !(obj instanceof NameAndType)) {
        return false;
      }
      NameAndType that = NameAndType.class.cast(obj);
      return this.name.equals(that.name) && this.type.equals(that.type);
    }

    @Override
    public String toString() {
      return "NameAndType(" + name + "," + type + ")";
    }
  }
}
