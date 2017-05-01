package p2p.components.common;

import java.io.File;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A PeerDescription object contains information about a specific peer
 * that can be used by the tracker to index the peer's shared files and
 * determine its contact infomariton.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class PeerDescription implements Serializable {
	
	/**
	 * The serialVersionID required by the {@link Serializable}
	 * interface to ensure the integrity of the object during a
	 * serialization and deserialization process.
	 */
	private static final long serialVersionUID = 4131872014857893328L;
	
	private final InetSocketAddress						 socket_address;
	private final HashMap<String, SharedFileDescription> shared_file_descriptions;
	
	/**
	 * Allocates a new PeerDescription object.
	 *
	 * @param socket_address
	 *        The {@link InetSocketAddress} object that describes the
	 *        server socket of the peer.
	 * @param shared_files
	 *        A list of the peer's shared files.
	 */
	public PeerDescription(InetSocketAddress socket_address, List<File> shared_files) {
		
		/*
		 * TODO Replace HashMap by Set.
		 */
		
		this.socket_address = socket_address;
		this.shared_file_descriptions = (HashMap<String, SharedFileDescription>) shared_files.parallelStream()
		        .collect(Collectors.toMap(x -> x.getName(), x -> new SharedFileDescription(x)));
		
	}
	
	/**
	 * Copy constructor of the PeerDescription object.
	 *
	 * @param object
	 *        The object to be copied.
	 */
	public PeerDescription(PeerDescription object) {
		
		this.socket_address = object.getSocketAddress();
		this.shared_file_descriptions = object.getSharedFileDescriptions();
		
	}
	
	/**
	 * Allocates a new PeerDescription object.
	 *
	 * @param host
	 *        The peer's host address.
	 * @param port
	 *        The peer's port number that the server is listening to.
	 * @param shared_files A list of the peer's shared files.
	 */
	public PeerDescription(String host, int port, List<File> shared_files) {
		this(new InetSocketAddress(host, port), shared_files);
	}
	
	/**
	 * @return The IP address of the peer's server.
	 */
	public String getHostAddress() {
		
		return this.socket_address.getAddress().getHostAddress();
	}
	
	/**
	 * @return The port number of the peer's server.
	 */
	public int getPort() {
		
		return this.socket_address.getPort();
	}
	
	/**
	 * @return A copy of files list.
	 */
	public HashMap<String, SharedFileDescription> getSharedFileDescriptions() {
		
		/*
		 * TODO Return Set instead of Map.
		 */
		
		return (HashMap<String, SharedFileDescription>) this.shared_file_descriptions.entrySet().parallelStream()
		        .collect(Collectors.toMap(x -> x.getKey(), x -> new SharedFileDescription(x.getValue())));
	}
	
	/**
	 * @return A copy of {@link InetSocketAddress} associated with the
	 *         server.
	 */
	public InetSocketAddress getSocketAddress() {
		
		return new InetSocketAddress(this.getHostAddress(), this.getPort());
	}
	
}
