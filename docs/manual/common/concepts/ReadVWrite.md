# Separating reads and write-side

Functionality to write, or persist data has completely different needs from functionality to read or query data. Separating read- and write-side concerns makes it possible to offer the best experience for both independently. A model that tries to encapsulate both read and write operations rarely does either well. 

With the [CQRS](https://msdn.microsoft.com/en-us/library/jj591573.aspx) pattern, the write-side entities focus on the updating commands and you can optimize the read-side for different types of queries and reporting jobs.  This separation enables better scalability, since the read-side can be scaled out to many nodes independently of the write-side, and its typically on the read-side that you need massive scalability. 

For example, in a bidding system it is important to "take the write" and respond to the bidder that we have accepted the bid as soon as possible, which means that write-throughput is of highest importance. The same application might have some complex statistics view or analysts might be working with the data to figure out best bidding strategies and trends. These read-side use cases typically require some kind of expressive query capabilities and a different data model than the write-side. A consequence of separating the read-side from the write-side is eventual consistency. An update to the write-side might not be visible to the read-side instantaneously.

<!--- The following diagram illustrates separation of reads from writes: (TBA) --->

