package com.streamhub.live;

import java.util.List;

import com.streamhub.common.api.ApiResponse;
import com.streamhub.common.api.RequestUserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class GiftController {
    private final GiftService giftService;

    public GiftController(GiftService giftService) {
        this.giftService = giftService;
    }

    @GetMapping("/gifts")
    public ApiResponse<List<GiftCatalog>> listGifts() {
        return ApiResponse.success(giftService.listGifts());
    }

    @GetMapping("/wallet")
    public ApiResponse<WalletView> wallet(HttpServletRequest request) {
        return ApiResponse.success(giftService.wallet(RequestUserId.required(request)));
    }

    @PostMapping("/wallet/recharge")
    public ApiResponse<WalletView> recharge(
            @Valid @RequestBody RechargeRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(giftService.recharge(
                RequestUserId.required(httpRequest),
                request.bizNo().trim(),
                request.amount()));
    }

    @PostMapping("/live/rooms/{roomId}/gifts")
    public ApiResponse<GiftOrderView> sendGift(
            @PathVariable long roomId,
            @Valid @RequestBody SendGiftRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(giftService.placeOrder(
                roomId,
                RequestUserId.required(httpRequest),
                request.giftCode(),
                request.quantity(),
                request.clientOrderNo()));
    }

    @GetMapping("/gift-orders/{orderNo}")
    public ApiResponse<GiftOrderView> getOrder(@PathVariable String orderNo) {
        return ApiResponse.success(giftService.findOrder(orderNo));
    }

    @GetMapping("/live/rooms/{roomId}/gift-rank")
    public ApiResponse<List<GiftRankEntry>> contributorRank(
            @PathVariable long roomId,
            @RequestParam(defaultValue = "10") @Min(1) int limit) {
        return ApiResponse.success(giftService.contributorRank(roomId, Math.min(limit, 100)));
    }

    @GetMapping("/live/rooms/{roomId}/gift-income-rank")
    public ApiResponse<List<GiftRankEntry>> incomeRank(
            @PathVariable long roomId,
            @RequestParam(defaultValue = "10") @Min(1) int limit) {
        return ApiResponse.success(giftService.incomeRank(roomId, Math.min(limit, 100)));
    }

    public record RechargeRequest(
            @NotBlank @Size(max = 128) String bizNo,
            @Min(1) long amount) {
    }

    public record SendGiftRequest(
            @NotBlank @Size(max = 64) String giftCode,
            @Min(1) int quantity,
            @NotBlank @Size(max = 128) String clientOrderNo) {
    }
}
