package denominator.ultradns;

import com.squareup.okhttp.mockwebserver.MockResponse;

import denominator.model.ResourceRecordSet;
import denominator.model.rdata.AData;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import denominator.ResourceRecordSetApi;
import org.junit.rules.ExpectedException;
import denominator.Credentials;
import feign.Feign;

import denominator.ultradns.InvalidatableTokenProvider.Session;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static denominator.model.ResourceRecordSets.a;
import static denominator.ultradns.UltraDNSMockResponse.*;
import static org.assertj.core.api.Assertions.assertThat;

public class UltraDNSRestResourceRecordSetApiMockTest {

  @Rule
  public final MockUltraDNSRestServer server = new MockUltraDNSRestServer();

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  /**
   * Mock API to call UltraDNSRest Endpoint
   * @return UltraDNSRest object
   */
  @Before
  public void mockServer() {
    UltraDNSRestProvider.FeignModule module = new UltraDNSRestProvider.FeignModule();
    UltraDNSRestProvider provider = new UltraDNSRestProvider() {
      @Override
      public String url() {
        return server.url();
      }
    };
    javax.inject.Provider<Credentials> credentials = new javax.inject.Provider<Credentials>() {
      @Override
      public Credentials get() {
        return server.credentials();
      }
    };
    AtomicReference<Boolean> sessionValid = module.sessionValid();
    UltraDNSRestErrorDecoder errorDecoder = new UltraDNSRestErrorDecoder(sessionValid);
    Feign feign = module.feign(module.logger(), module.logLevel(), errorDecoder);
    Session session = feign.newInstance(new SessionTarget(provider));

    InvalidatableTokenProvider tokenProvider = new InvalidatableTokenProvider(provider,
            session, credentials, sessionValid);
    tokenProvider.lastCredentialsHashCode = credentials.get().hashCode();
    tokenProvider.token = "token";
    sessionValid.set(true);
  }

  @Test
  public void listWhenNoneMatch() throws Exception {
    thrown.expect(UltraDNSRestException.class);
    thrown.expectMessage("Zone does not exist in the system.");

    server.enqueueSessionResponse();
    server.enqueue(new MockResponse()
            .setResponseCode(404)
            .setBody(UltraDNSMockResponse.getMockErrorResponse(
                    "" + UltraDNSRestException.ZONE_NOT_FOUND,
                    "Zone does not exist in the system.")));

    ResourceRecordSetApi api = server.connect().api()
            .basicRecordSetsInZone("non-existent-zone.io.");
    // This calls the UltraDNS#getResourceRecordsOfZone().
    api.iterator();
  }

  @Test
  public void iterateByNameWhenNoneMatch() throws Exception {
    thrown.expect(UltraDNSRestException.class);
    thrown.expectMessage("Data not found.");

    server.enqueueSessionResponse();
    server.enqueue(new MockResponse()
            .setResponseCode(404)
            .setBody(UltraDNSMockResponse.getMockErrorResponse(
                    "" + UltraDNSRestException.DATA_NOT_FOUND,
                    "Data not found.")));

    ResourceRecordSetApi api = server.connect().api()
            .basicRecordSetsInZone("denominator.io.");
    // This calls the UltraDNS#getResourceRecordsOfDNameByType().
    api.iterateByName("non-existent-subdomain.denominator.io.");
  }

  @Test
  public void getByNameAndTypeWhenAbsent() throws Exception {
    server.enqueueSessionResponse();
    server.enqueue(new MockResponse()
            .setResponseCode(404)
            .setBody(UltraDNSMockResponse.getMockErrorResponse(
                    "" + UltraDNSRestException.DATA_NOT_FOUND,
                    "Data not found.")));

    ResourceRecordSetApi api = server.connect().api()
            .basicRecordSetsInZone("denominator.io.");
    // This calls the UltraDNS#getResourceRecordsOfDNameByType().
    api.getByNameAndType("www.denominator.io.", "A");
    server.assertSessionRequest();
    server.assertRequest()
            .hasMethod("GET")
            .hasPath("/zones/denominator.io./rrsets/1/www.denominator.io.")
            .hasBody("");
  }

  @Test
  public void getByNameAndTypeWhenPresent() throws Exception {
    server.enqueueSessionResponse();
    server.enqueue(new MockResponse().setBody(GET_RESOURCE_RECORDS_PRESENT));

    ResourceRecordSetApi api = server.connect().api().basicRecordSetsInZone("denominator.io.");
    ResourceRecordSet<AData> aDataResourceRecordSet = a("pool_2.denominator.io.", 86400,
            Arrays.asList("1.1.1.1", "2.2.2.2", "3.3.3.3", "4.4.4.4",
                    "5.5.5.5", "6.6.6.6", "7.7.7.7"));
    assertThat(api.getByNameAndType("pool_2.denominator.io.", "A"))
            .isEqualTo(aDataResourceRecordSet);
    server.assertSessionRequest();
    server.assertRequest()
            .hasMethod("GET")
            .hasPath("/zones/denominator.io./rrsets/1/pool_2.denominator.io.");
  }

}
