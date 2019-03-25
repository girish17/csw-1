package csw.network.utils

import java.net.{InetAddress, NetworkInterface}

import csw.network.utils.exceptions.NetworkInterfaceNotFound
import csw.network.utils.internal.NetworkInterfaceProvider
import org.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FunSuite, Matchers}

import scala.collection.JavaConverters.enumerationAsScalaIteratorConverter

class NetworksTest extends FunSuite with Matchers with BeforeAndAfterAll with BeforeAndAfterEach with MockitoSugar {

  test("Should filter ipv6 addresses") {
    val mockedNetworkProvider = mock[NetworkInterfaceProvider]
    val inet4Address          = InetAddress.getByAddress(Array[Byte](192.toByte, 168.toByte, 1, 2))
    val inet6Address = InetAddress.getByAddress(
      Array[Byte](192.toByte,
                  168.toByte,
                  1,
                  2,
                  192.toByte,
                  168.toByte,
                  1,
                  2,
                  192.toByte,
                  168.toByte,
                  1,
                  2,
                  192.toByte,
                  168.toByte,
                  1,
                  2)
    )
    when(mockedNetworkProvider.getInterface("eth0")).thenReturn(Seq((1, List(inet6Address, inet4Address))))
    val ipv4Address: InetAddress = new Networks("eth0", mockedNetworkProvider).ipv4AddressWithInterfaceName._2
    ipv4Address shouldEqual inet4Address

  }

  test("Should get ip4 address of interface with lowest index when interface name is not provided") {
    val inet4Address1         = InetAddress.getByAddress(Array[Byte](192.toByte, 168.toByte, 1, 2))
    val inet4Address2         = InetAddress.getByAddress(Array[Byte](172.toByte, 17.toByte, 1, 2))
    val inet4Address3         = InetAddress.getByAddress(Array[Byte](10.toByte, 12.toByte, 2, 1))
    val mockedNetworkProvider = mock[NetworkInterfaceProvider]
    when(mockedNetworkProvider.allInterfaces)
      .thenReturn(Seq((1, List(inet4Address1)), (2, List(inet4Address2)), (3, List(inet4Address3))))

    Networks("", mockedNetworkProvider).ipv4AddressWithInterfaceName._2 shouldBe inet4Address1
  }

  test("testGetIpv4Address throws NetworkInterfaceNotFound when provided interface name is not present") {
    a[NetworkInterfaceNotFound] shouldBe thrownBy(Networks(Some("test")))
  }

  test("testGetIpv4Address returns inet address when provided a valid interface name") {
    val inetAddresses: List[(String, InetAddress)] =
      NetworkInterface.getNetworkInterfaces.asScala.toList.map { iface ⇒
        Networks(Some(iface.getName)).ipv4AddressWithInterfaceName
      }

    inetAddresses.contains(("LocalHost", InetAddress.getLocalHost)) shouldEqual true
  }
}
