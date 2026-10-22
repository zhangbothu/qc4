package com.mapsearch.qc.sample;

import java.util.List;

/** Vocabulary used to synthesize a realistic-looking POI corpus for four markets. */
final class SampleCatalog {

    private SampleCatalog() {
    }

    record City(int id, String name) {
    }

    record Spec(int countryId, String code, String countryName, List<City> cities, List<String> brands,
                List<String> categories, List<String> nicknames, List<String> persons, List<String> saints,
                List<String> streets, List<String> areas, List<String> landmarks) {
    }

    static final int MX = 484;
    static final int GB = 826;
    static final int MY = 458;
    static final int ID = 360;

    static List<City> cities(int base, String... names) {
        City[] c = new City[names.length];
        for (int i = 0; i < names.length; i++) {
            c[i] = new City(base * 10000 + i + 1, names[i]);
        }
        return List.of(c);
    }

    static final Spec MEXICO = new Spec(MX, "MX", "Mexico",
            cities(MX, "Ciudad de Mexico", "Ecatepec de Morelos", "Guadalajara", "Monterrey", "Puebla", "Tijuana",
                    "Cuernavaca", "Naucalpan de Juarez", "Leon", "Merida"),
            List.of("Starbucks Coffee", "OXXO", "Walmart", "Bodega Aurrera", "Soriana", "Chedraui", "Coppel", "Elektra",
                    "Liverpool", "Farmacias Guadalajara", "Farmacias del Ahorro", "Farmacias Similares", "Banco Azteca",
                    "BBVA", "Banorte", "Santander", "Telcel", "Pemex", "Home Depot", "Sanborns", "Suburbia", "Costco",
                    "Sams Club", "Burger King", "McDonald's", "Domino's Pizza", "Little Caesars", "KFC", "Vips", "Toks",
                    "Italian Coffee Company", "Cinepolis", "Cinemex", "Office Depot", "Waldos", "Del Sol", "Woolworth",
                    "Super Kompras", "Aurrera Express", "Farmacia San Pablo"),
            List.of("Taqueria", "Tortilleria", "Panaderia", "Papeleria", "Ferreteria", "Farmacia", "Restaurante",
                    "Cafeteria", "Lavanderia", "Estetica", "Carniceria", "Fruteria", "Abarrotes", "Refaccionaria",
                    "Taller Mecanico", "Consultorio Dental", "Clinica", "Gimnasio", "Hotel", "Materiales para Construccion",
                    "Pasteleria", "Pizzeria", "Mariscos", "Polleria", "Cerrajeria", "Veterinaria", "Optica",
                    "Muebleria", "Purificadora de Agua", "Loncheria"),
            List.of("El Paisa", "El Guero", "La Esperanza", "El Buen Sabor", "Don Pepe", "La Guadalupana",
                    "Los Compadres", "El Fogon", "La Michoacana", "El Rey", "La Reyna", "Los Arcos", "La Estrella",
                    "El Triunfo", "La Providencia", "Axel", "Hermanos Lopez", "San Rafael", "El Sol", "La Luna",
                    "Dona Mary", "El Chino", "Las Palmas", "La Bendicion", "El Torito", "Los Primos"),
            List.of("Benito Juarez", "Miguel Hidalgo", "Emiliano Zapata", "Jose Maria Morelos", "Ignacio Zaragoza",
                    "Lazaro Cardenas", "Francisco Villa", "Vicente Guerrero", "Sor Juana Ines de la Cruz",
                    "Guadalupe Victoria", "Venustiano Carranza", "Adolfo Lopez Mateos", "Ninos Heroes", "Leona Vicario",
                    "Josefa Ortiz de Dominguez", "Ignacio Allende", "Justo Sierra", "Jaime Torres Bodet"),
            List.of("San Juan Bautista", "Santa Maria", "San Francisco de Asis", "Nuestra Senora de Guadalupe",
                    "San Miguel Arcangel", "Santo Nino de Atocha", "San Judas Tadeo", "Sagrado Corazon de Jesus",
                    "San Jose", "Santa Cecilia"),
            List.of("Insurgentes", "Reforma", "Morelos", "Hidalgo", "Juarez", "Zaragoza", "Allende", "Madero",
                    "Revolucion", "Independencia", "Constitucion", "Aldama", "Guerrero", "Matamoros", "Bravo", "Victoria",
                    "Galeana", "Abasolo", "Obregon", "Carranza", "Chapultepec", "Tlalpan", "Universidad",
                    "Division del Norte", "Central", "Las Torres", "Lopez Mateos", "Periferico", "Nacional", "Vallarta"),
            List.of("Centro", "Del Valle", "Roma Norte", "Condesa", "Polanco", "Narvarte", "Coyoacan", "Tlalpan",
                    "Iztapalapa", "Azcapotzalco", "Ciudad Azteca", "Jardines de Morelos", "Las Americas", "San Cristobal",
                    "Guadalupe Victoria", "Ejidal", "Las Fuentes", "Santa Clara", "Valle de Aragon", "Lindavista",
                    "Providencia", "Chapalita", "Mitras", "Cumbres", "La Paz", "Santa Cruz", "San Pedro", "El Carmen",
                    "Los Reyes", "Nueva Atzacoalco", "Tecamac", "Santa Fe", "Doctores", "Industrial"),
            List.of("Angel de la Independencia", "Palacio de Bellas Artes", "Museo Nacional de Antropologia",
                    "Bosque de Chapultepec", "Zocalo", "Basilica de Guadalupe", "Estadio Azteca",
                    "Torre Latinoamericana", "Monumento a la Revolucion", "Xochimilco", "Teotihuacan",
                    "Aeropuerto Internacional de la Ciudad de Mexico", "Central de Abastos",
                    "Plaza Las Americas Ecatepec", "Mundo E", "Six Flags Mexico", "Hospital Angeles",
                    "Universidad Nacional Autonoma de Mexico", "Catedral Metropolitana", "Arena Mexico",
                    "Palacio Municipal de Ecatepec", "Hospital General de Ecatepec", "Parque Ecologico Ehecatl"));

    static final Spec UK = new Spec(GB, "GB", "United Kingdom",
            cities(GB, "London", "Manchester", "Birmingham", "Liverpool", "Leeds", "Glasgow", "Edinburgh", "Bristol",
                    "Nottingham", "Cambridge"),
            List.of("Tesco Express", "Tesco Extra", "Sainsbury's Local", "Sainsbury's", "Asda", "Morrisons", "Waitrose",
                    "Marks and Spencer", "Boots", "Superdrug", "Costa Coffee", "Starbucks Coffee", "Caffe Nero",
                    "Pret A Manger", "Greggs", "McDonald's", "Burger King", "Nando's", "Wagamama", "Pizza Express", "KFC",
                    "Subway", "Apple Store", "Argos", "Primark", "Currys", "Halfords", "Lloyds Bank", "Barclays", "HSBC",
                    "NatWest", "Santander", "Post Office", "Premier Inn", "Travelodge", "Holiday Inn", "Odeon Cinema",
                    "Vue Cinema", "Wetherspoon", "Shell", "Esso", "Sports Direct", "JD Sports", "Specsavers", "Lidl",
                    "Aldi", "Iceland", "Co-op Food", "Poundland", "Waterstones"),
            List.of("Primary School", "Academy", "Medical Centre", "Dental Practice", "Pharmacy", "Library",
                    "Leisure Centre", "Community Centre", "Fire Station", "Police Station", "Train Station",
                    "Bus Station", "Car Park", "Park", "Church", "Methodist Church", "Baptist Church", "Mosque",
                    "Surgery", "Hospital", "Barbers", "Launderette", "Fish and Chips", "Indian Restaurant",
                    "Chinese Takeaway", "Cafe", "Hotel", "Garage", "Nursery", "Recreation Ground", "Allotments",
                    "Health Centre", "Veterinary Clinic", "Bakery", "Butchers"),
            List.of("The Red Lion", "The Crown", "The Royal Oak", "The White Hart", "The Swan", "The Plough",
                    "The Bell", "The Kings Arms", "The Queens Head", "The George", "The Rose and Crown",
                    "The Black Horse", "The Ship", "The Fox and Hounds", "The Green Man", "The Wheatsheaf",
                    "The Railway Tavern", "The Coach and Horses", "The Prince of Wales", "The Anchor"),
            List.of("Street", "Road", "Lane", "Avenue", "Close", "Way", "Drive", "Place", "Gardens", "Crescent",
                    "Terrace", "Hill", "Square", "Grove", "Court"),
            List.of("St Mary's", "St John's", "St Peter's", "St Paul's", "St Andrew's", "St Michael's",
                    "Holy Trinity", "All Saints", "Christ Church", "St George's", "St James's", "St Margaret's"),
            List.of("High", "Church", "Station", "Victoria", "Park", "London", "Queens", "Kings", "Mill", "Manor",
                    "Green", "Oxford", "Baker", "Regent", "Bridge", "Castle", "Market", "Chapel", "School", "North",
                    "South", "West", "Albert", "Windsor", "Newport", "Elm", "Springfield", "Highfield", "Westminster",
                    "Tower", "Whitechapel", "Camden", "Brixton", "Hampstead", "Kensington", "Portobello", "Edgware",
                    "Bishopsgate", "Shaftesbury", "Piccadilly"),
            List.of("Camden", "Islington", "Hackney", "Westminster", "Southwark", "Lambeth", "Greenwich", "Brixton",
                    "Shoreditch", "Kensington", "Chelsea", "Wimbledon", "Croydon", "Stratford", "Hammersmith",
                    "Fulham", "Salford", "Didsbury", "Edgbaston", "Headingley", "Clifton", "Leith", "Partick", "Anfield",
                    "Toxteth", "Moseley", "Bloomsbury", "Mayfair", "Soho", "Whitechapel", "Canary Wharf", "Tower Hamlets"),
            List.of("The Tower of London", "Tower Bridge", "London Bridge", "Big Ben", "Buckingham Palace",
                    "Westminster Abbey", "Houses of Parliament", "British Museum", "Natural History Museum",
                    "Science Museum", "Victoria and Albert Museum", "Tate Modern", "National Gallery",
                    "Trafalgar Square", "Piccadilly Circus", "Covent Garden", "Camden Market", "Borough Market",
                    "Hyde Park", "Regent's Park", "London Eye", "St Paul's Cathedral", "Kings Cross Station",
                    "Paddington Station", "Waterloo Station", "Liverpool Street Station", "Heathrow Airport",
                    "Gatwick Airport", "Wembley Stadium", "Old Trafford", "Anfield Stadium", "Edinburgh Castle",
                    "Royal Mile", "Manchester Piccadilly Station", "Birmingham New Street Station", "Tower Hill",
                    "Royal Albert Hall", "Shakespeare's Globe", "Madame Tussauds", "Harrods", "Selfridges", "The Shard",
                    "Sky Garden", "Kew Gardens", "Royal Observatory Greenwich", "Cutty Sark", "The O2 Arena",
                    "Apple Regent Street", "Apple Covent Garden", "Apple Battersea"));

    static final Spec MALAYSIA = new Spec(MY, "MY", "Malaysia",
            cities(MY, "Kuala Lumpur", "Petaling Jaya", "Shah Alam", "Johor Bahru", "George Town", "Ipoh",
                    "Kota Kinabalu", "Kuching", "Melaka", "Subang Jaya"),
            List.of("Petronas", "Shell", "Petron", "Caltex", "Maybank", "CIMB Bank", "Public Bank", "RHB Bank",
                    "Hong Leong Bank", "Bank Islam", "Bank Rakyat", "AmBank", "Mydin", "Lotus's", "Giant", "AEON",
                    "Econsave", "KK Super Mart", "FamilyMart", "Watsons", "Guardian", "Caring Pharmacy",
                    "Starbucks Coffee", "ZUS Coffee", "Tealive", "Old Town White Coffee", "Secret Recipe", "McDonald's",
                    "KFC", "Marrybrown", "Pizza Hut", "Domino's Pizza", "Subway", "Nando's", "Texas Chicken", "MR DIY",
                    "Machines Apple Premium Reseller", "Pos Malaysia", "Uniqlo", "Popular Bookstore", "Harvey Norman",
                    "Senheng", "Celcom", "Maxis", "Unifi", "Sushi King", "Kenny Rogers Roasters", "Village Grocer"),
            List.of("Kedai Makan", "Restoran", "Restoran Nasi Kandar", "Kedai Runcit", "Klinik", "Klinik Kesihatan",
                    "Klinik Pergigian", "Farmasi", "Tadika", "Kedai Kopi", "Bengkel Kereta", "Kedai Gunting Rambut",
                    "Dobi", "Pasar Malam", "Pasar Awam", "Dewan Orang Ramai", "Padang Bola", "Kedai Perabot",
                    "Kedai Telefon", "Hotel", "Gerai Makan", "Kedai Roti", "Butik", "Pusat Tuisyen"),
            List.of("Al Ameen", "Ali", "Ahmad", "Siti", "Murni", "Seri", "Mawar", "Melati", "Cahaya", "Bintang", "Jaya",
                    "Maju", "Bahagia", "Sentosa", "Indah", "Mutiara", "Delima", "Permata", "Harmoni", "Damai",
                    "Haji Ismail", "Mak Teh", "Pak Mat", "Kak Ros", "Tok Wan", "Al Hidayah", "Nur Iman", "Ar Rahman"),
            List.of("Tun Hussein Onn", "Tunku Abdul Rahman", "Tun Abdul Razak", "Sultan Ismail", "Hang Tuah"),
            List.of("Masjid", "Surau", "Kuil", "Gereja"),
            List.of("Bukit Bintang", "Ampang", "Tun Razak", "Sultan Ismail", "Raja Chulan", "Tuanku Abdul Rahman",
                    "Petaling", "Pudu", "Imbi", "Cheras", "Klang Lama", "Ipoh", "Gasing", "Universiti", "Templer",
                    "Kuchai Lama", "Sentul", "Kepong", "Bangsar", "Telawi", "Maarof", "Damansara", "Kota Raja",
                    "Merdeka", "Masjid India", "Hang Tuah", "Hang Jebat", "Tun Perak", "Pahang", "Kelang", "Perdana"),
            List.of("Taman Melawati", "Taman Tun Dr Ismail", "Taman Desa", "Taman Connaught", "Taman Midah",
                    "Taman Bukit Maluri", "Taman Sri Gombak", "Taman Megah", "Taman Paramount", "Taman Seri Muda",
                    "Bandar Baru Bangi", "Bandar Utama", "Bandar Sunway", "Setapak", "Wangsa Maju", "Ampang Jaya",
                    "Puchong", "Seri Kembangan", "Kajang", "Rawang", "Klang", "Bangsar", "Mont Kiara", "Sri Hartamas",
                    "Damansara Utama", "Kota Damansara", "Cyberjaya", "Putrajaya", "Sentul", "Brickfields"),
            List.of("Petronas Twin Towers", "Kuala Lumpur City Centre", "Suria KLCC", "Menara Kuala Lumpur",
                    "Batu Caves", "Pavilion Kuala Lumpur", "Mid Valley Megamall", "Sunway Pyramid",
                    "Kuala Lumpur International Airport", "KL Sentral", "Masjid Negara", "Masjid Jamek",
                    "Dataran Merdeka", "Istana Negara", "Muzium Negara", "Taman Tasik Perdana", "Jalan Alor",
                    "Petaling Street", "Central Market", "Berjaya Times Square", "Thean Hou Temple",
                    "Genting Highlands", "The Exchange TRX", "Apple The Exchange TRX", "Universiti Malaya",
                    "Hospital Kuala Lumpur", "Stadium Bukit Jalil", "Aquaria KLCC", "Sunway Lagoon", "IOI City Mall"));

    static final Spec INDONESIA = new Spec(ID, "ID", "Indonesia",
            cities(ID, "Jakarta", "Surabaya", "Bandung", "Bekasi", "Tangerang", "Depok", "Medan", "Semarang",
                    "Yogyakarta", "Denpasar"),
            List.of("Indomaret", "Alfamart", "Alfamidi", "Superindo", "Hypermart", "Transmart", "Lotte Mart", "Hero",
                    "Matahari", "Ramayana", "Gramedia", "Apotek Kimia Farma", "Apotek Century", "Guardian",
                    "Watsons", "Bank Mandiri", "Bank BCA", "Bank BRI", "Bank BNI", "Bank Syariah Indonesia",
                    "SPBU Pertamina", "Shell", "Starbucks Coffee", "Kopi Kenangan", "Janji Jiwa", "Fore Coffee",
                    "Excelso", "McDonald's", "KFC", "Pizza Hut", "Burger King", "Richeese Factory", "Solaria", "Hokben",
                    "Es Teh Indonesia", "Mie Gacoan", "Sate Khas Senayan", "Bakmi GM", "Erafone",
                    "iBox Apple Premium Reseller", "Ace Hardware", "Informa", "Pegadaian", "JNE Express",
                    "Kantor Pos Indonesia", "Telkomsel", "Indosat", "Chatime", "Roti O"),
            List.of("Warung Makan", "Rumah Makan Padang", "Warung Kopi", "Toko Kelontong", "Toko Bangunan", "Toko Obat",
                    "Apotek", "Klinik", "Puskesmas", "Rumah Sakit", "Bengkel Motor", "Bengkel Mobil", "Salon", "Laundry",
                    "Pangkas Rambut", "Pasar", "Lapangan", "Hotel", "Kos", "Bakso", "Mie Ayam", "Sate", "Nasi Goreng",
                    "Ayam Geprek", "Martabak", "Soto", "Toko Roti", "Toko Emas", "Konter Pulsa", "Fotokopi"),
            List.of("Barokah", "Berkah", "Sejahtera", "Makmur", "Jaya", "Abadi", "Sumber Rezeki", "Bu Sri",
                    "Pak Kumis", "Mas Bejo", "Cak Har", "Mbak Yuni", "Sederhana", "Sinar", "Mulya", "Lestari",
                    "Al Ikhlas", "Al Falah", "Al Hidayah", "An Nur", "Nurul Huda", "Baitul Rahman", "Al Muhajirin",
                    "Mekar", "Indah", "Bahari", "Sentosa", "Mandiri", "Harapan", "Cahaya"),
            List.of("Sudirman", "Diponegoro", "Kartini", "Ahmad Yani", "Imam Bonjol"),
            List.of("Masjid", "Mushola", "Gereja", "Pura", "Vihara"),
            List.of("Sudirman", "Thamrin", "Gatot Subroto", "Rasuna Said", "Diponegoro", "Ahmad Yani", "Gajah Mada",
                    "Hayam Wuruk", "Pemuda", "Merdeka", "Pahlawan", "Veteran", "Kartini", "Imam Bonjol",
                    "Pangeran Antasari", "Fatmawati", "Kemang Raya", "Raya Bogor", "Margonda Raya", "Juanda",
                    "Asia Afrika", "Malioboro", "Basuki Rahmat", "Raya Darmo", "Gubeng", "Pajajaran", "Siliwangi",
                    "Cikini Raya", "Senopati", "Kebon Jeruk"),
            List.of("Menteng", "Kebayoran Baru", "Tebet", "Cilandak", "Pasar Minggu", "Kemang", "Senayan",
                    "Tanah Abang", "Cempaka Putih", "Kelapa Gading", "Cengkareng", "Grogol", "Pondok Indah", "Cibubur",
                    "Ciputat", "Serpong", "Bintaro", "Cibinong", "Bekasi Timur", "Bekasi Barat", "Margahayu", "Dago",
                    "Coblong", "Gubeng", "Wonokromo", "Rungkut", "Sukolilo", "Medan Baru", "Kuta", "Sanur"),
            List.of("Monumen Nasional", "Masjid Istiqlal", "Gereja Katedral Jakarta", "Taman Mini Indonesia Indah",
                    "Ancol", "Dunia Fantasi", "Kota Tua Jakarta", "Grand Indonesia", "Plaza Indonesia", "Pacific Place",
                    "Senayan City", "Plaza Senayan", "Gelora Bung Karno", "Bandara Soekarno Hatta", "Stasiun Gambir",
                    "Stasiun Pasar Senen", "Bundaran HI", "Pasar Tanah Abang", "Pasar Baru", "Kebun Binatang Ragunan",
                    "Candi Borobudur", "Candi Prambanan", "Jalan Malioboro", "Gedung Sate", "Tangkuban Perahu",
                    "Pantai Kuta", "Tanah Lot", "Tugu Pahlawan", "Jembatan Suramadu", "Kebun Raya Bogor"));

    static final List<Spec> ALL = List.of(MEXICO, UK, MALAYSIA, INDONESIA);
}
