package com.example.data

data class Helpline(
    val name: String,
    val number: String,
    val description: String,
    val category: String // "Emergency", "Women Specialist", "Support"
)

data class SafetyTip(
    val title: String,
    val description: String,
    val category: String // "Precautions", "Self Defense", "Travel"
)

object HelplinesAndTips {
    val helplines = listOf(
        Helpline(
            name = "National Emergency Number",
            number = "112",
            description = "All-in-one emergency service helpline for police, medical assistance, and rescue.",
            category = "Emergency"
        ),
        Helpline(
            name = "Women Helpline (All India)",
            number = "1091",
            description = "Specialized 24/7 helpline for women facing physical harassment, abuse, or danger.",
            category = "Women Specialist"
        ),
        Helpline(
            name = "Women Helpline (Domestic Violence)",
            number = "181",
            description = "Support for domestic violence, mental harassment, or familial disputes.",
            category = "Women Specialist"
        ),
        Helpline(
            name = "Police Control Room (PCR)",
            number = "100",
            description = "Direct line to closest active police command unit dispatchers.",
            category = "Emergency"
        ),
        Helpline(
            name = "Ambulance Services",
            number = "102",
            description = "Emergency municipal health and ambulance transport services.",
            category = "Emergency"
        ),
        Helpline(
            name = "Student Helpline",
            number = "1098",
            description = "Support, counselling, and shelter assistance for children/students in stress.",
            category = "Support"
        )
    )

    val tips = listOf(
        SafetyTip(
            title = "Share Live Location Privately",
            description = "When walking alone late or boarding a cab, share your live GPS location with close family members until you arrive safely inside.",
            category = "Travel"
        ),
        SafetyTip(
            title = "Trust Your Intuition",
            description = "If a situation, alleyway, or individual feels wrong or uncomfortable, trust your gut. Leave immediately, enter a public store, or run to public safety.",
            category = "Precautions"
        ),
        SafetyTip(
            title = "Stay in Well-Lit Transit Routes",
            description = "Avoid dark shortcuts, unmonitored pathways, or isolated parks. Walk in the center of the sidewalk facing oncoming traffic so you cannot be surprised.",
            category = "Travel"
        ),
        SafetyTip(
            title = "Aim for Vulnerable Points",
            description = "If forced to defend yourself, strike vulnerability spots: eyes, throat, groin, or shins. Use the heel of your palm, car keys, or heavy purse to create distance.",
            category = "Self Defense"
        ),
        SafetyTip(
            title = "Create Visible/Audible Attention",
            description = "If followed or restricted, shout fire or use the SafeHer Siren Alarm to draw immediate public eyes. Attackers hate attention.",
            category = "Self Defense"
        ),
        SafetyTip(
            title = "Keep Your Phone Ready",
            description = "Keep your thumb on the volume or power key (or trigger SafeHer via shaking). If you sense threat, immediately pre-stage the crisis button ready.",
            category = "Precautions"
        )
    )
}
