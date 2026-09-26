package emu.grasscutter.server.packet.send;


import emu.grasscutter.data.GameData;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.VehicleInteractRspOuterClass.VehicleInteractRsp;
import emu.grasscutter.net.proto.VehicleInteractTypeOuterClass.VehicleInteractType;
import emu.grasscutter.net.proto.VehicleMemberOuterClass.VehicleMember;
import java.util.Locale;
public class PacketVehicleInteractRsp extends BasePacket {

    public PacketVehicleInteractRsp(Player player, int entityId, VehicleInteractType interactType) {
        super(PacketOpcodes.VehicleInteractRsp);
        VehicleInteractRsp.Builder proto = VehicleInteractRsp.newBuilder();

        GameEntity vehicle = player.getScene().getEntityById(entityId);
        vehicle.setFightProperty(1010,1000);
        vehicle.setFightProperty(2000,1000);
        if (vehicle instanceof EntityVehicle) {
            proto.setEntityId(vehicle.getId());

            VehicleMember vehicleMember =
                    VehicleMember.newBuilder()
                            .setUid(player.getUid())
                            .setAvatarGuid(player.getTeamManager().getCurrentCharacterGuid())
                            .build();

            proto.setInteractType(interactType);
            proto.setMember(vehicleMember);

            switch (interactType) {
                case VehicleInteractType_VEHICLE_INTERACT_IN -> {
                    ((EntityVehicle) vehicle).getVehicleMembers().add(vehicleMember);
                    player
                            .getQuestManager()
                            .queueEvent(
                                    QuestContent.QUEST_CONTENT_ENTER_VEHICLE,
                                    ((EntityVehicle) vehicle).getGadgetId());

                    var jsonName = GameData.getGadgetDataMap().get(((EntityVehicle) vehicle).getGadgetId())
                            .getJsonName().toLowerCase(Locale.ROOT);
                    var vehicleType = jsonName.contains("skiff") ? 2 : jsonName.contains("sorush") ? 3 :
                            jsonName.contains("natsaurus") ? 4 : 1;
                    proto.setGadgetId(((EntityVehicle) vehicle).getGadgetId());
                    proto.setVehicleType(vehicleType);
                    proto.setVehiclePos(player.getPosition().toProto());
                    proto.setVehicleRot(player.getRotation().toProto());
                }
                case VehicleInteractType_VEHICLE_INTERACT_OUT -> {
                    ((EntityVehicle) vehicle).getVehicleMembers().remove(vehicleMember);
                }
                default -> {}
            }
        }
        this.setData(proto.build());
    }

    public PacketVehicleInteractRsp(
            EntityVehicle vehicle, VehicleMember vehicleMember, VehicleInteractType interactType) {
        super(PacketOpcodes.VehicleInteractRsp);
        VehicleInteractRsp.Builder proto = VehicleInteractRsp.newBuilder();

        if (vehicle != null) {
            proto.setEntityId(vehicle.getId());
            proto.setInteractType(interactType);
            proto.setMember(vehicleMember);

            switch (interactType) {
                case VehicleInteractType_VEHICLE_INTERACT_IN -> {
                    vehicle.getVehicleMembers().add(vehicleMember);
                }
                case VehicleInteractType_VEHICLE_INTERACT_OUT -> {
                    vehicle.getVehicleMembers().remove(vehicleMember);
                }
                default -> {}
            }
        }
        this.setData(proto.build());
    }
}
