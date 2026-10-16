package io.github.currencortex.music.data.effects

import kotlinx.serialization.json.*

/** APK 9.5.30 ff1/c$b.f packaged fallback for play#speakerEffect; no device identity is spoofed.
 * Remote custom configuration is intentionally not claimed by this packaged fallback.
 */
internal fun originalSpeakerDeviceOverride(model: String, id: Long): OriginalSpeakerMaterial? {
    val slot = when (id) {
        -100L -> "landscape" to "L1"; -101L -> "landscape" to "L2"; -102L -> "landscape" to "L3"
        -200L -> "portrait" to "L1"; -201L -> "portrait" to "L2"; -202L -> "portrait" to "L3"
        -300L -> "bathroom" to ""; -400L -> "sleep" to ""
        else -> return null
    }
    val row = (speakerDeviceFallback["deviceConfigs"] as JsonArray).filterIsInstance<JsonObject>().firstOrNull {
        (it["deviceModels"] as? JsonArray)?.any { name -> (name as? JsonPrimitive)?.contentOrNull == model } == true
    } ?: return null
    val mappings = row["effectMappings"] as? JsonObject ?: return null
    val orientation = mappings[slot.first] as? JsonObject ?: return null
    val material = if (slot.second.isEmpty()) orientation else orientation[slot.second] as? JsonObject ?: return null
    // ff1/c.d keeps the selected generic negative ID, using the mapped resource metadata.
    return parseOriginalSpeakerMaterial(buildJsonObject {
        put("materialId", id); put("materialName", material["materialName"] ?: JsonPrimitive("外放音效"))
        put("meta", material["url"] ?: JsonNull); put("md5", material["md5"] ?: JsonNull)
        put("fileSize", material["fileSize"] ?: JsonNull)
    })
}

private val speakerDeviceFallback by lazy { Json.parseToJsonElement(SPEAKER_DEVICE_FALLBACK).jsonObject }
private const val SPEAKER_DEVICE_FALLBACK = """
{
  "deviceConfigs": [
    {
      "deviceModels": [
        "RVL-AL09"
      ],
      "effectMappings": {
        "landscape": {
          "L3": {
            "materialId": "-1003",
            "materialName": "RVL-AL09_Landscape_L3",
            "url": "https://d1.music.126.net/dmusic/obj/w5zCg8OAw6HDjzjDgMK_/77933587531/7234/2228/38c0/b340b65f0f15bd665fcb92e106454b36.ncae",
            "fileSize": 329,
            "md5": "b340b65f0f15bd665fcb92e106454b36"
          },
          "L2": {
            "materialId": "-1004",
            "materialName": "RVL-AL09_Landscape_L2",
            "url": "https://d1.music.126.net/dmusic/obj/w5zCg8OAw6HDjzjDgMK_/77933586742/a80a/4411/5929/58f527be10953b101bb713cae910b9d5.ncae",
            "fileSize": 330,
            "md5": "58f527be10953b101bb713cae910b9d5"
          },
          "L1": {
            "materialId": "-1005",
            "materialName": "RVL-AL09_Landscape_L1",
            "url": "https://d1.music.126.net/dmusic/obj/w5zCg8OAw6HDjzjDgMK_/77933590290/af98/abf2/0bc2/d56d6f309cd52840693cdda97b271db9.ncae",
            "fileSize": 327,
            "md5": "d56d6f309cd52840693cdda97b271db9"
          }
        },
        "portrait": {
          "L3": {
            "materialId": "-1006",
            "materialName": "RVL-AL09_Portrait_L3",
            "url": "https://d2.music.126.net/dmusic/obj/w5zCg8OAw6HDjzjDgMK_/77933587531/7234/2228/38c0/b340b65f0f15bd665fcb92e106454b36.ncae",
            "fileSize": 329,
            "md5": "b340b65f0f15bd665fcb92e106454b36"
          },
          "L2": {
            "materialId": "-1007",
            "materialName": "RVL-AL09_Portrait_L2",
            "url": "https://d1.music.126.net/dmusic/obj/w5zCg8OAw6HDjzjDgMK_/77933586742/a80a/4411/5929/58f527be10953b101bb713cae910b9d5.ncae",
            "fileSize": 330,
            "md5": "58f527be10953b101bb713cae910b9d5"
          },
          "L1": {
            "materialId": "-1008",
            "materialName": "RVL-AL09_Portrait_L1",
            "url": "https://d2.music.126.net/dmusic/obj/w5zCg8OAw6HDjzjDgMK_/77933590290/af98/abf2/0bc2/d56d6f309cd52840693cdda97b271db9.ncae",
            "fileSize": 327,
            "md5": "d56d6f309cd52840693cdda97b271db9"
          }
        }
      }
    },
    {
      "deviceModels": [
        "M1810E5A",
        "M1810E5E",
        "M1810E5GG"
      ],
      "effectMappings": {
        "landscape": {
          "L3": {
            "materialId": "-1009",
            "materialName": "M1810E_Landscape_L3",
            "url": "https://d1.music.126.net/dmusic/obj/w5zCg8OAw6HDjzjDgMK_/77933587531/7234/2228/38c0/b340b65f0f15bd665fcb92e106454b36.ncae",
            "fileSize": 329,
            "md5": "b340b65f0f15bd665fcb92e106454b36"
          },
          "L2": {
            "materialId": "-1010",
            "materialName": "M1810E_Landscape_L2",
            "url": "https://d1.music.126.net/dmusic/obj/w5zCg8OAw6HDjzjDgMK_/77933586742/a80a/4411/5929/58f527be10953b101bb713cae910b9d5.ncae",
            "fileSize": 330,
            "md5": "58f527be10953b101bb713cae910b9d5"
          },
          "L1": {
            "materialId": "-1011",
            "materialName": "M1810E_Landscape_L1",
            "url": "https://d1.music.126.net/dmusic/obj/w5zCg8OAw6HDjzjDgMK_/77933590290/af98/abf2/0bc2/d56d6f309cd52840693cdda97b271db9.ncae",
            "fileSize": 327,
            "md5": "d56d6f309cd52840693cdda97b271db9"
          }
        },
        "portrait": {
          "L3": {
            "materialId": "-1012",
            "materialName": "M1810E_Portrait_L3",
            "url": "https://d2.music.126.net/dmusic/obj/w5zCg8OAw6HDjzjDgMK_/77933587531/7234/2228/38c0/b340b65f0f15bd665fcb92e106454b36.ncae",
            "fileSize": 329,
            "md5": "b340b65f0f15bd665fcb92e106454b36"
          },
          "L2": {
            "materialId": "-1013",
            "materialName": "M1810E_Portrait_L2",
            "url": "https://d1.music.126.net/dmusic/obj/w5zCg8OAw6HDjzjDgMK_/77933586742/a80a/4411/5929/58f527be10953b101bb713cae910b9d5.ncae",
            "fileSize": 330,
            "md5": "58f527be10953b101bb713cae910b9d5"
          },
          "L1": {
            "materialId": "-1014",
            "materialName": "M1810E_Portrait_L1",
            "url": "https://d1.music.126.net/dmusic/obj/w5zCg8OAw6HDjzjDgMK_/77933590290/af98/abf2/0bc2/d56d6f309cd52840693cdda97b271db9.ncae",
            "fileSize": 327,
            "md5": "d56d6f309cd52840693cdda97b271db9"
          }
        }
      }
    },
    {
      "deviceModels": [
        "SHARK KSR-A0"
      ],
      "effectMappings": {
        "landscape": {
          "L3": {
            "materialId": "-1015",
            "materialName": "SHARK_KSR-A0_Landscape_L3",
            "url": "https://d1.music.126.net/dmusic/obj/w5zCg8OAw6HDjzjDgMK_/77933590291/adc3/99ad/2905/2fb7576f772df4f06ac6179e2d1afff6.ncae",
            "fileSize": 277,
            "md5": "2fb7576f772df4f06ac6179e2d1afff6"
          },
          "L2": {
            "materialId": "-1016",
            "materialName": "SHARK_KSR-A0_Landscape_L2",
            "url": "https://d2.music.126.net/dmusic/obj/w5zCg8OAw6HDjzjDgMK_/77933588492/d5f3/f85b/bad6/26f484b62b2018bc9f2c21d629e835a3.ncae",
            "fileSize": 282,
            "md5": "26f484b62b2018bc9f2c21d629e835a3"
          },
          "L1": {
            "materialId": "-1017",
            "materialName": "SHARK_KSR-A0_Landscape_L1",
            "url": "https://d1.music.126.net/dmusic/obj/w5zCg8OAw6HDjzjDgMK_/77933588492/d5f3/f85b/bad6/26f484b62b2018bc9f2c21d629e835a3.ncae",
            "fileSize": 282,
            "md5": "26f484b62b2018bc9f2c21d629e835a3"
          }
        }
      }
    },
    {
      "deviceModels": [
        "M2001J1C",
        "M2001J1E",
        "M2001J1G",
        "M2001J2C",
        "M2001J2E",
        "M2001J2G",
        "M2001J2I"
      ],
      "effectMappings": {
        "landscape": {
          "L3": {
            "materialId": "-1018",
            "materialName": "M2001J_Landscape_L3",
            "url": "https://d1.music.126.net/dmusic/obj/w5zCg8OAw6HDjzjDgMK_/77933590291/adc3/99ad/2905/2fb7576f772df4f06ac6179e2d1afff6.ncae",
            "fileSize": 277,
            "md5": "2fb7576f772df4f06ac6179e2d1afff6"
          },
          "L2": {
            "materialId": "-1019",
            "materialName": "M2001J_Landscape_L2",
            "url": "https://d1.music.126.net/dmusic/obj/w5zCg8OAw6HDjzjDgMK_/77933588492/d5f3/f85b/bad6/26f484b62b2018bc9f2c21d629e835a3.ncae",
            "fileSize": 282,
            "md5": "26f484b62b2018bc9f2c21d629e835a3"
          },
          "L1": {
            "materialId": "-1020",
            "materialName": "M2001J_Landscape_L1",
            "url": "https://d2.music.126.net/dmusic/obj/w5zCg8OAw6HDjzjDgMK_/77933588492/d5f3/f85b/bad6/26f484b62b2018bc9f2c21d629e835a3.ncae",
            "fileSize": 282,
            "md5": "26f484b62b2018bc9f2c21d629e835a3"
          }
        }
      }
    },
    {
      "deviceModels": [
        "SHARK KTUS-A0",
        "SHARK KTUS-H0",
        "SHARK PAR-A0",
        "SHARK PAR-H0"
      ],
      "effectMappings": {
        "landscape": {
          "L3": {
            "materialId": "-1021",
            "materialName": "SHARK_Landscape_L3",
            "url": "https://d2.music.126.net/dmusic/obj/w5zCg8OAw6HDjzjDgMK_/77933590874/ec35/0c45/721a/3f3c01a1c1fed1c888986d098f487a2e.ncae",
            "fileSize": 280,
            "md5": "3f3c01a1c1fed1c888986d098f487a2e"
          },
          "L2": {
            "materialId": "-1022",
            "materialName": "SHARK_Landscape_L2",
            "url": "https://d2.music.126.net/dmusic/obj/w5zCg8OAw6HDjzjDgMK_/77933588492/d5f3/f85b/bad6/26f484b62b2018bc9f2c21d629e835a3.ncae",
            "fileSize": 282,
            "md5": "26f484b62b2018bc9f2c21d629e835a3"
          },
          "L1": {
            "materialId": "-1023",
            "materialName": "SHARK_Landscape_L1",
            "url": "https://d2.music.126.net/dmusic/obj/w5zCg8OAw6HDjzjDgMK_/77933588492/d5f3/f85b/bad6/26f484b62b2018bc9f2c21d629e835a3.ncae",
            "fileSize": 282,
            "md5": "26f484b62b2018bc9f2c21d629e835a3"
          }
        }
      }
    }
  ]
}
"""
