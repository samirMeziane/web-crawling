package webcrawler.mactans.webdocument;

/**
 * This class represents a URL object.
 *
 */
public abstract class URL {

	/**
	 * URL type: a URL is either of type HTML or other types.
	 * 
	 * @author 675lt
	 *
	 */
	public enum URLType {
		HTML, OTHER
	}

	/**
	 * The address of this URL.
	 */
	public String address = "";

	/**
	 * The fully qualified content type as written in the HTTP header for this URL.
	 */
	public String actualType = "";

	/**
	 * The content size as written in the HTTP header for this URL.
	 */
	public String size = "";

	/**
	 * Simplified version of actualType.
	 */
	public URLType type = URLType.HTML;

	/**
	 * Constructs a new URL object.
	 * 
	 * @param address the address for this URL object
	 * @param actualType content type as written in the HTTP header for this URL
	 * @param size content size as written in the HTTP header for this URL
	 * @param type simplified version of actualType, it can either be HTML or OTHER
	 */
	public URL(String address, String actualType, String size, URLType type) {
		this.address = address;
		this.actualType = actualType;
		this.size = size;
		this.type = type;
	}

	public String getActualType() {
		return actualType;
	}

	public void setActualType(String actualType) {
		this.actualType = actualType;
	}

	public void setType(URLType type) {
		this.type = type;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getType() {
		return type.toString();
	}

	public String getSize() {
		return size;
	}

	public void setSize(String size) {
		this.size = size;
	}

}
